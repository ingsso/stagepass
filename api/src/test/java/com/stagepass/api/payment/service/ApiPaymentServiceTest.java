package com.stagepass.api.payment.service;

import com.stagepass.api.payment.dto.PaymentConfirmRequest;
import com.stagepass.api.payment.dto.PaymentInitRequest;
import com.stagepass.api.payment.dto.PaymentInitResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.payment.PaymentRepository;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.PendingPaymentRedisRepository;
import com.stagepass.kafka.event.PaymentRequestedEvent;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ApiPaymentServiceTest {

  @InjectMocks private ApiPaymentService apiPaymentService;

  @Mock private ReservationRepository reservationRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private PendingPaymentRedisRepository pendingPaymentRepository;
  @Mock private EventPublisher eventPublisher;

  private static final Long USER_ID = 1L;
  private static final Long RESERVATION_ID = 10L;
  private static final int TOTAL_PRICE = 50000;
  private static final String ORDER_ID = "order-abc";
  private static final String PAYMENT_KEY = "pay-key-xyz";

  private Reservation reservation;

  @BeforeEach
  void setUp() {
    User user = User.builder()
        .email("test@test.com").passwordHash("hash").name("테스트").role(UserRole.USER).build();
    ReflectionTestUtils.setField(user, "id", USER_ID);

    Performance performance = Performance.builder().title("테스트 공연").build();
    ReflectionTestUtils.setField(performance, "id", 1L);

    Show show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(show, "id", 100L);

    reservation = Reservation.builder().user(user).show(show).totalPrice(TOTAL_PRICE).build();
    ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
  }

  @Test
  @DisplayName("결제 승인 — 금액 일치 시 Kafka 이벤트 발행")
  void confirmPayment_성공() {
    PaymentConfirmRequest request = new PaymentConfirmRequest();
    ReflectionTestUtils.setField(request, "orderId", ORDER_ID);
    ReflectionTestUtils.setField(request, "paymentKey", PAYMENT_KEY);
    ReflectionTestUtils.setField(request, "amount", TOTAL_PRICE);

    given(pendingPaymentRepository.get(ORDER_ID)).willReturn(RESERVATION_ID);
    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
        .willReturn(Optional.of(reservation));
    given(paymentRepository.findByTossOrderId(ORDER_ID)).willReturn(Optional.empty());

    apiPaymentService.confirmPayment(USER_ID, request);

    ArgumentCaptor<PaymentRequestedEvent> captor = ArgumentCaptor.forClass(PaymentRequestedEvent.class);
    then(eventPublisher).should().publishPaymentRequested(captor.capture());
    assertThat(captor.getValue().getAmount()).isEqualTo(TOTAL_PRICE);
    assertThat(captor.getValue().getTossOrderId()).isEqualTo(ORDER_ID);
  }

  @Test
  @DisplayName("결제 승인 — 금액 불일치 시 PAYMENT_AMOUNT_MISMATCH 예외")
  void confirmPayment_금액불일치_실패() {
    PaymentConfirmRequest request = new PaymentConfirmRequest();
    ReflectionTestUtils.setField(request, "orderId", ORDER_ID);
    ReflectionTestUtils.setField(request, "paymentKey", PAYMENT_KEY);
    ReflectionTestUtils.setField(request, "amount", 1); // 조작된 금액

    given(pendingPaymentRepository.get(ORDER_ID)).willReturn(RESERVATION_ID);
    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
        .willReturn(Optional.of(reservation));

    assertThatThrownBy(() -> apiPaymentService.confirmPayment(USER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.PAYMENT_AMOUNT_MISMATCH.getMessage());

    then(eventPublisher).should(never()).publishPaymentRequested(any());
  }

  @Test
  @DisplayName("결제 승인 — orderId 만료 시 INVALID_TOKEN 예외")
  void confirmPayment_orderId만료_실패() {
    PaymentConfirmRequest request = new PaymentConfirmRequest();
    ReflectionTestUtils.setField(request, "orderId", ORDER_ID);
    ReflectionTestUtils.setField(request, "paymentKey", PAYMENT_KEY);
    ReflectionTestUtils.setField(request, "amount", TOTAL_PRICE);

    given(pendingPaymentRepository.get(ORDER_ID)).willReturn(null);

    assertThatThrownBy(() -> apiPaymentService.confirmPayment(USER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.INVALID_TOKEN.getMessage());
  }

  @Test
  @DisplayName("결제 승인 — 중복 orderId 시 DUPLICATE_PAYMENT 예외")
  void confirmPayment_중복orderId_실패() {
    PaymentConfirmRequest request = new PaymentConfirmRequest();
    ReflectionTestUtils.setField(request, "orderId", ORDER_ID);
    ReflectionTestUtils.setField(request, "paymentKey", PAYMENT_KEY);
    ReflectionTestUtils.setField(request, "amount", TOTAL_PRICE);

    given(pendingPaymentRepository.get(ORDER_ID)).willReturn(RESERVATION_ID);
    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
        .willReturn(Optional.of(reservation));
    given(paymentRepository.findByTossOrderId(ORDER_ID))
        .willReturn(Optional.of(com.stagepass.domain.payment.Payment.builder()
            .reservation(reservation).tossOrderId(ORDER_ID).amount(TOTAL_PRICE).build()));

    assertThatThrownBy(() -> apiPaymentService.confirmPayment(USER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.DUPLICATE_PAYMENT.getMessage());

    then(eventPublisher).should(never()).publishPaymentRequested(any());
  }
}
