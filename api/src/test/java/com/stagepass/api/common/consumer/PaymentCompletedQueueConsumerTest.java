package com.stagepass.api.common.consumer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.api.queue.service.QueueService;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRole;
import com.stagepass.kafka.event.PaymentResultEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedQueueConsumerTest {

  @InjectMocks private PaymentCompletedQueueConsumer consumer;

  @Mock private ReservationRepository reservationRepository;
  @Mock private QueueEntryRepository queueEntryRepository;
  @Mock private QueueService queueService;
  @Mock private ObjectMapper objectMapper;
  @Mock private Acknowledgment ack;

  private static final Long RESERVATION_ID = 1L;
  private static final Long USER_ID = 10L;
  private static final Long SHOW_ID = 100L;

  private Reservation reservation;

  @BeforeEach
  void setUp() {
    User user = User.builder()
        .email("test@test.com").passwordHash("hash").name("테스터").role(UserRole.USER).build();

    Performance performance = Performance.builder().title("테스트 공연").build();
    Show show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(show, "id", SHOW_ID);

    reservation = Reservation.builder().user(user).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
  }

  @Test
  @DisplayName("결제 완료 — ACTIVATED 유저 결제 후 다음 배치 활성화")
  void handlePaymentCompleted_ACTIVATED유저_다음배치활성화() throws Exception {
    PaymentResultEvent event = new PaymentResultEvent(RESERVATION_ID, USER_ID, "order_abc", null);
    given(objectMapper.readValue(any(String.class), any(Class.class))).willReturn(event);
    given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

    QueueEntry entry = QueueEntry.builder().show(reservation.getShow()).user(
        User.builder().email("t@t.com").passwordHash("h").name("t").role(UserRole.USER).build()
    ).rank(1).build();
    entry.activate();
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.of(entry));

    consumer.handlePaymentCompleted("message", ack);

    then(queueService).should().activateNextBatch(SHOW_ID);
    then(ack).should().acknowledge();
  }

  @Test
  @DisplayName("결제 완료 — WAITING 유저면 다음 배치 활성화 없음")
  void handlePaymentCompleted_WAITING유저_배치활성화없음() throws Exception {
    PaymentResultEvent event = new PaymentResultEvent(RESERVATION_ID, USER_ID, "order_abc", null);
    given(objectMapper.readValue(any(String.class), any(Class.class))).willReturn(event);
    given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

    QueueEntry entry = QueueEntry.builder().show(reservation.getShow()).user(
        User.builder().email("t@t.com").passwordHash("h").name("t").role(UserRole.USER).build()
    ).rank(5).build(); // WAITING 상태
    given(queueEntryRepository.findByShowIdAndUserId(SHOW_ID, USER_ID))
        .willReturn(Optional.of(entry));

    consumer.handlePaymentCompleted("message", ack);

    then(queueService).should(never()).activateNextBatch(any());
    then(ack).should().acknowledge();
  }

  @Test
  @DisplayName("결제 완료 — 잘못된 JSON (포이즌 필) → ack 처리, 재시도 없음")
  void handlePaymentCompleted_포이즌필_ack처리() throws Exception {
    given(objectMapper.readValue(any(String.class), any(Class.class)))
        .willThrow(new JsonParseException(null, "bad json"));

    consumer.handlePaymentCompleted("bad-json", ack);

    then(ack).should().acknowledge();
    then(queueService).should(never()).activateNextBatch(any());
  }

  @Test
  @DisplayName("결제 완료 — 처리 중 예외 발생 시 RuntimeException으로 재시도 위임")
  void handlePaymentCompleted_처리예외_재시도위임() throws Exception {
    PaymentResultEvent event = new PaymentResultEvent(RESERVATION_ID, USER_ID, "order_abc", null);
    given(objectMapper.readValue(any(String.class), any(Class.class))).willReturn(event);
    given(reservationRepository.findById(RESERVATION_ID))
        .willThrow(new RuntimeException("DB 연결 실패"));

    assertThatThrownBy(() -> consumer.handlePaymentCompleted("message", ack))
        .isInstanceOf(RuntimeException.class);

    then(ack).should(never()).acknowledge();
  }
}
