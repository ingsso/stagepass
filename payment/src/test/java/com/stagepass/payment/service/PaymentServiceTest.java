package com.stagepass.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.domain.payment.Payment;
import com.stagepass.domain.payment.PaymentRepository;
import com.stagepass.domain.payment.PaymentStatus;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRole;
import com.stagepass.kafka.event.PaymentCancelEvent;
import com.stagepass.kafka.event.PaymentRequestedEvent;
import com.stagepass.kafka.event.PaymentResultEvent;
import com.stagepass.kafka.producer.EventPublisher;
import com.stagepass.payment.client.TossPaymentClient;
import com.stagepass.payment.dto.TossPaymentConfirmRequest;
import com.stagepass.payment.dto.TossPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @InjectMocks
  private PaymentService paymentService;

  @Mock private PaymentRepository paymentRepository;
  @Mock private ReservationRepository reservationRepository;
  @Mock private TossPaymentClient tossPaymentClient;
  @Mock private EventPublisher eventPublisher;
  @Mock private ObjectMapper objectMapper;

  private Reservation reservation;
  private PaymentRequestedEvent event;

  @BeforeEach
  void setUp() {
    User user = User.builder()
        .email("test@test.com")
        .passwordHash("encoded")
        .name("홍길동")
        .role(UserRole.USER)
        .build();

    Performance performance = Performance.builder()
        .title("뮤지컬 테스트")
        .build();

    Show show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();

    reservation = Reservation.builder()
        .user(user)
        .show(show)
        .totalPrice(100000)
        .build();

    event = new PaymentRequestedEvent(1L, 1L, 100000, "order_abc", "payment_key_xyz");
  }

  @Test
  @DisplayName("결제 처리 성공 - Toss API 성공 → payment.completed 발행")
  void processPayment_성공_결제완료이벤트발행() {
    // given
    given(paymentRepository.findByTossOrderId(event.getTossOrderId()))
        .willReturn(Optional.empty());
    given(reservationRepository.findById(event.getReservationId()))
        .willReturn(Optional.of(reservation));
    given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    TossPaymentResponse tossResponse = new TossPaymentResponse();
    ReflectionTestUtils.setField(tossResponse, "paymentKey", "payment_key_xyz");
    ReflectionTestUtils.setField(tossResponse, "method", "카드");
    given(tossPaymentClient.confirm(any(TossPaymentConfirmRequest.class)))
        .willReturn(tossResponse);

    // when
    paymentService.processPayment(event);

    // then
    then(eventPublisher).should().publishPaymentCompleted(any(PaymentResultEvent.class));
    then(eventPublisher).should(never()).publishPaymentFailed(any());
  }

  @Test
  @DisplayName("결제 처리 실패 - Toss API 오류 → payment.failed 발행 (보상 트랜잭션)")
  void processPayment_TossAPI실패_결제실패이벤트발행() {
    // given
    given(paymentRepository.findByTossOrderId(event.getTossOrderId()))
        .willReturn(Optional.empty());
    given(reservationRepository.findById(event.getReservationId()))
        .willReturn(Optional.of(reservation));
    given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    willThrow(new RuntimeException("토스페이먼츠 결제 승인 실패"))
        .given(tossPaymentClient).confirm(any());

    // when
    paymentService.processPayment(event);

    // then
    then(eventPublisher).should().publishPaymentFailed(any(PaymentResultEvent.class));
    then(eventPublisher).should(never()).publishPaymentCompleted(any());
  }

  @Test
  @DisplayName("결제 멱등성 - 동일 orderId 재요청 시 처리 무시")
  void processPayment_중복orderId_처리무시() {
    // given
    Payment existingPayment = Payment.builder()
        .reservation(reservation)
        .tossOrderId(event.getTossOrderId())
        .amount(100000)
        .build();
    given(paymentRepository.findByTossOrderId(event.getTossOrderId()))
        .willReturn(Optional.of(existingPayment));

    // when
    paymentService.processPayment(event);

    // then
    then(tossPaymentClient).should(never()).confirm(any());
    then(eventPublisher).should(never()).publishPaymentCompleted(any());
    then(eventPublisher).should(never()).publishPaymentFailed(any());
  }

  @Test
  @DisplayName("취소 멱등성 — 이미 CANCELLED 상태이면 Toss API 미호출")
  void cancelPayment_이미취소됨_멱등처리() {
    Payment cancelledPayment = Payment.builder()
        .reservation(reservation)
        .tossOrderId("order_abc")
        .amount(100000)
        .build();
    ReflectionTestUtils.invokeMethod(cancelledPayment, "cancel"); // status → CANCELLED

    given(paymentRepository.findByReservationId(1L)).willReturn(Optional.of(cancelledPayment));

    paymentService.cancelPayment(1L);

    then(tossPaymentClient).should(never()).cancel(any(), any(), any());
  }

  @Test
  @DisplayName("취소 처리 — Toss API 실패 시 DB 상태 변경 없이 예외 전파")
  void cancelPayment_TossAPI실패_DB상태변경없음() {
    Payment payment = Payment.builder()
        .reservation(reservation)
        .tossOrderId("order_abc")
        .amount(100000)
        .build();
    ReflectionTestUtils.setField(payment, "tossPaymentKey", "payment_key_xyz");

    given(paymentRepository.findByReservationId(1L)).willReturn(Optional.of(payment));
    willThrow(new RuntimeException("Toss 취소 실패"))
        .given(tossPaymentClient).cancel(any(), any(), any());

    assertThatThrownBy(() -> paymentService.cancelPayment(1L))
        .isInstanceOf(RuntimeException.class);

    // DB 상태가 CANCELLED로 변경되지 않았음을 확인 (payment.cancel() 미호출)
    then(paymentRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("취소 요청 Consumer — 역직렬화 실패 시 RuntimeException으로 재시도 위임")
  void handlePaymentCancelRequested_역직렬화실패_재시도위임() throws Exception {
    given(objectMapper.readValue("bad-json", PaymentCancelEvent.class))
        .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "bad json"));

    assertThatThrownBy(() ->
        paymentService.handlePaymentCancelRequested("bad-json", null))
        .isInstanceOf(RuntimeException.class);
  }
}
