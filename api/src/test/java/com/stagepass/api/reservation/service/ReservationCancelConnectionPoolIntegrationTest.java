package com.stagepass.api.reservation.service;

import com.stagepass.api.queue.service.QueueService;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.PerformanceRepository;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.infra.redis.WaitlistRedisRepository;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

/**
 * 예매 취소 동시 요청 시 커넥션 풀 자기 교착 재현 테스트.
 *
 * 실제 PostgreSQL + HikariCP(풀 10) 위에서 ReservationService.cancel() 을 10건 동시에 실행한다.
 * 취소 이벤트 발행 시점(외부 트랜잭션이 커넥션을 쥔 상태)에서 10개 스레드를 배리어로 맞춰,
 * 풀의 커넥션 10개가 전부 외부 트랜잭션에 점유된 순간을 만든다.
 *
 * 수정 전: cancel() 이 REQUIRES_NEW 인 notifyNext() 를 호출해 두 번째 커넥션을 요구 →
 *         아무도 얻지 못하고 connection-timeout 후 전부 실패(취소 롤백).
 * 수정 후: notifyNext() 는 Kafka 컨슈머가 실행하므로 cancel() 은 커넥션 1개만 쓴다 → 전부 성공.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReservationService.class, WaitlistService.class})
class ReservationCancelConnectionPoolIntegrationTest {

  private static final int POOL_SIZE = 10;  // HikariCP 기본값과 동일

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> POOL_SIZE);
    registry.add("spring.datasource.hikari.connection-timeout", () -> 2000);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired private ReservationService reservationService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private ShowRepository showRepository;
  @Autowired private PerformanceRepository performanceRepository;
  @Autowired private UserRepository userRepository;

  @MockBean private SeatRedisRepository seatRedisRepository;
  @MockBean private WaitlistRedisRepository waitlistRedisRepository;
  @MockBean private EventPublisher eventPublisher;
  @MockBean private QueueService queueService;

  private final List<Reservation> reservations = new ArrayList<>();

  @BeforeEach
  void setUp() {
    Performance performance = performanceRepository.save(Performance.builder().title("테스트 공연").build());
    Show show = showRepository.save(Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build());

    for (int i = 0; i < POOL_SIZE; i++) {
      User user = userRepository.save(User.builder()
          .email("user" + i + "@test.com").passwordHash("h").name("테스터" + i).role(UserRole.USER).build());
      reservations.add(reservationRepository.save(
          Reservation.builder().user(user).show(show).totalPrice(50000).build()));
    }
  }

  @AfterEach
  void tearDown() {
    reservationRepository.deleteAll();
    userRepository.deleteAll();
    showRepository.deleteAll();
    performanceRepository.deleteAll();
    reservations.clear();
  }

  @Test
  @DisplayName("풀 크기만큼 동시 취소해도 커넥션 교착 없이 전부 성공한다")
  void cancel_동시요청_풀크기_교착없음() throws Exception {
    // 취소 이벤트 발행 = 외부 트랜잭션이 커넥션을 쥐고 있는 지점. 10건이 모두 여기 도달할 때까지 대기
    CyclicBarrier allHoldingConnection = new CyclicBarrier(POOL_SIZE);
    willAnswer(inv -> {
      allHoldingConnection.await(10, TimeUnit.SECONDS);
      return null;
    }).given(eventPublisher).publishReservationCancelled(any());

    AtomicInteger success = new AtomicInteger();
    Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

    ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
    long start = System.currentTimeMillis();
    List<Future<?>> futures = new ArrayList<>();
    for (Reservation r : reservations) {
      futures.add(executor.submit(() -> {
        try {
          reservationService.cancel(r.getId(), r.getUser().getId());
          success.incrementAndGet();
        } catch (Exception e) {
          failures.computeIfAbsent(e.getClass().getSimpleName(), k -> new AtomicInteger()).incrementAndGet();
        }
      }));
    }
    for (Future<?> f : futures) {
      f.get(60, TimeUnit.SECONDS);
    }
    executor.shutdown();
    long elapsed = System.currentTimeMillis() - start;

    long cancelled = reservationRepository.findAll().stream()
        .filter(r -> r.getStatus() == ReservationStatus.CANCELLED)
        .count();
    System.out.printf("[pool-deadlock] success=%d/%d cancelledInDb=%d failures=%s elapsed=%dms%n",
        success.get(), POOL_SIZE, cancelled, failures, elapsed);

    assertThat(success.get()).isEqualTo(POOL_SIZE);
    assertThat(cancelled).isEqualTo(POOL_SIZE);
  }
}
