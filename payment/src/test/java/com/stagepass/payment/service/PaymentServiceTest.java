package com.stagepass.payment.service;

import com.stagepass.domain.payment.Payment;
import com.stagepass.domain.payment.PaymentRepository;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRole;
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
}
