package com.stagepass.api.queue.service;

import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.NotificationEvent;
import com.stagepass.kafka.event.SeatHoldEvent;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReservationExpirySchedulerTest {

  @InjectMocks private ReservationExpiryScheduler scheduler;

  @Mock private ReservationRepository reservationRepository;
  @Mock private ReservationSeatRepository reservationSeatRepository;
  @Mock private SeatRedisRepository seatRedisRepository;
  @Mock private EventPublisher eventPublisher;
  @Mock private WaitlistService waitlistService;
  @Mock private QueueService queueService;
  @Mock private QueueEntryRepository queueEntryRepository;

  private User user;
  private Show show;

  @BeforeEach
  void setUp() {
    user = User.builder()
        .email("test@test.com").passwordHash("hash").name("테스터").role(UserRole.USER).build();
    ReflectionTestUtils.setField(user, "id", 1L);

    Performance performance = Performance.builder().title("테스트 공연").build();
    ReflectionTestUtils.setField(performance, "id", 1L);

    show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(show, "id", 100L);
  }

  @Test
  @DisplayName("processNextBatch — 만료 예매 처리 시 만료 상태로 변경 + 알림 발행")
  void processNextBatch_만료예매_상태변경_알림발행() {
    Reservation reservation = Reservation.builder().user(user).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(reservation, "id", 10L);

    given(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
        .willReturn(List.of(reservation));
    given(reservationSeatRepository.findByReservationIdWithSeat(10L)).willReturn(List.of());
    given(queueEntryRepository.findByShowIdAndUserId(100L, 1L)).willReturn(Optional.empty());

    List<Reservation> result = scheduler.processNextBatch();

    assertThat(result).hasSize(1);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    then(eventPublisher).should().publishNotification(any(NotificationEvent.class));
    then(waitlistService).should().notifyNext(100L);
  }

  @Test
  @DisplayName("processNextBatch — 만료 예매 없으면 빈 리스트 반환")
  void processNextBatch_만료없음_빈리스트() {
    given(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
        .willReturn(List.of());

    List<Reservation> result = scheduler.processNextBatch();

    assertThat(result).isEmpty();
    then(eventPublisher).should(never()).publishNotification(any());
    then(waitlistService).should(never()).notifyNext(any());
  }

  @Test
  @DisplayName("expireReservations — 배치 크기보다 적으면 1회만 실행")
  void expireReservations_단일배치_종료() {
    Reservation reservation = Reservation.builder().user(user).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(reservation, "id", 10L);

    given(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
        .willReturn(List.of(reservation));
    given(reservationSeatRepository.findByReservationIdWithSeat(10L)).willReturn(List.of());
    given(queueEntryRepository.findByShowIdAndUserId(100L, 1L)).willReturn(Optional.empty());

    scheduler.expireReservations();

    // 배치 크기(100)보다 적으므로 findExpiredReservations 1회만 호출
    then(reservationRepository).should(times(1)).findExpiredReservations(any(), any(Pageable.class));
  }

  @Test
  @DisplayName("expireReservations — 처리 건수 0이면 로그 미출력 (eventPublisher 미호출)")
  void expireReservations_빈배치_이벤트미발행() {
    given(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
        .willReturn(List.of());

    scheduler.expireReservations();

    then(eventPublisher).should(never()).publishNotification(any());
  }
}
