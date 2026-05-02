package com.stagepass.api.common.consumer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentFailureConsumerTest {

  @InjectMocks private PaymentFailureConsumer consumer;

  @Mock private ReservationRepository reservationRepository;
  @Mock private WaitlistService waitlistService;
  @Mock private ObjectMapper objectMapper;
  @Mock private Acknowledgment ack;

  private static final Long RESERVATION_ID = 10L;
  private static final Long SHOW_ID = 100L;

  private Show show;

  @BeforeEach
  void setUp() {
    Performance performance = Performance.builder().title("테스트 공연").build();
    ReflectionTestUtils.setField(performance, "id", 1L);

    show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(show, "id", SHOW_ID);
  }

  @Test
  @DisplayName("결제 실패 정상 처리 — waitlistService.notifyNext 호출 + ack")
  void handlePaymentFailed_성공() throws Exception {
    PaymentResultEvent event = new PaymentResultEvent(RESERVATION_ID, 1L, "order-1", "결제 실패");

    User user = User.builder().email("t@t.com").passwordHash("h").name("테스터").role(UserRole.USER).build();
    Reservation reservation = Reservation.builder().user(user).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

    given(objectMapper.readValue(any(String.class), eq(PaymentResultEvent.class))).willReturn(event);
    given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

    consumer.handlePaymentFailed("{}", ack);

    then(waitlistService).should().notifyNext(SHOW_ID);
    then(ack).should().acknowledge();
  }

  @Test
  @DisplayName("결제 실패 — 예매 없으면 notifyNext 호출 안 함 (정상 ack)")
  void handlePaymentFailed_예매없음_notifyNext미호출() throws Exception {
    PaymentResultEvent event = new PaymentResultEvent(RESERVATION_ID, 1L, "order-1", "결제 실패");

    given(objectMapper.readValue(any(String.class), eq(PaymentResultEvent.class))).willReturn(event);
    given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

    consumer.handlePaymentFailed("{}", ack);

    then(waitlistService).should(never()).notifyNext(any());
    then(ack).should().acknowledge();
  }

  @Test
  @DisplayName("포이즌 필 — JSON 파싱 실패 시 ack (재시도 없음)")
  void handlePaymentFailed_포이즌필_ack() throws Exception {
    given(objectMapper.readValue(any(String.class), eq(PaymentResultEvent.class)))
        .willThrow(new JsonParseException(null, "bad json"));

    consumer.handlePaymentFailed("{bad}", ack);

    then(ack).should().acknowledge();
    then(waitlistService).should(never()).notifyNext(any());
  }

  @Test
  @DisplayName("일반 예외 — RuntimeException 재시도 위임 (ack 없음)")
  void handlePaymentFailed_일반예외_재시도() throws Exception {
    given(objectMapper.readValue(any(String.class), eq(PaymentResultEvent.class)))
        .willThrow(new RuntimeException("DB 장애"));

    assertThatThrownBy(() -> consumer.handlePaymentFailed("{}", ack))
        .isInstanceOf(RuntimeException.class);

    then(ack).should(never()).acknowledge();
  }
}
