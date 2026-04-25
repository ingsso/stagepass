package com.stagepass.api.reservation.service;

import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.PaymentCancelEvent;
import com.stagepass.kafka.event.ReservationEvent;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @InjectMocks private ReservationService reservationService;

  @Mock private ReservationRepository reservationRepository;
  @Mock private ReservationSeatRepository reservationSeatRepository;
  @Mock private SeatRedisRepository seatRedisRepository;
  @Mock private EventPublisher eventPublisher;
  @Mock private WaitlistService waitlistService;

  private static final Long USER_ID = 1L;
  private static final Long RESERVATION_ID = 10L;
  private static final Long SHOW_ID = 100L;

  private User user;
  private Show show;
  private Reservation confirmedReservation;
  private Reservation pendingReservation;

  @BeforeEach
  void setUp() {
    user = User.builder()
        .email("test@test.com").passwordHash("hash").name("테스트").role(UserRole.USER).build();
    ReflectionTestUtils.setField(user, "id", USER_ID);

    Performance performance = Performance.builder().title("테스트 공연").build();
    ReflectionTestUtils.setField(performance, "id", 1L);

    show = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(1))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(show, "id", SHOW_ID);

    confirmedReservation = Reservation.builder().user(user).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(confirmedReservation, "id", RESERVATION_ID);
    ReflectionTestUtils.setField(confirmedReservation, "status", ReservationStatus.CONFIRMED);

    pendingReservation = Reservation.builder().user(user).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(pendingReservation, "id", RESERVATION_ID);
    // PENDING 은 builder 기본값
  }

  @Test
  @DisplayName("예매 취소 — CONFIRMED 예매 취소 시 환불 이벤트 발행")
  void cancel_확정예매_환불이벤트발행() {
    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
        .willReturn(Optional.of(confirmedReservation));
    given(reservationSeatRepository.findByReservationId(RESERVATION_ID))
        .willReturn(List.of());

    reservationService.cancel(RESERVATION_ID, USER_ID);

    ArgumentCaptor<PaymentCancelEvent> captor = ArgumentCaptor.forClass(PaymentCancelEvent.class);
    then(eventPublisher).should().publishPaymentCancelRequested(captor.capture());
    assertThat(captor.getValue().getReservationId()).isEqualTo(RESERVATION_ID);
    assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);

    then(eventPublisher).should().publishReservationCancelled(any(ReservationEvent.class));
  }

  @Test
  @DisplayName("예매 취소 — PENDING 예매 취소 시 환불 이벤트 미발행")
  void cancel_미결제예매_환불이벤트미발행() {
    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
        .willReturn(Optional.of(pendingReservation));
    given(reservationSeatRepository.findByReservationId(RESERVATION_ID))
        .willReturn(List.of());

    reservationService.cancel(RESERVATION_ID, USER_ID);

    then(eventPublisher).should(never()).publishPaymentCancelRequested(any());
    then(eventPublisher).should().publishReservationCancelled(any(ReservationEvent.class));
  }

  @Test
  @DisplayName("예매 취소 — 본인 예매가 아니면 RESERVATION_NOT_FOUND 예외")
  void cancel_타인예매_실패() {
    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, USER_ID))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> reservationService.cancel(RESERVATION_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.RESERVATION_NOT_FOUND.getMessage());

    then(eventPublisher).should(never()).publishPaymentCancelRequested(any());
  }
}
