package com.stagepass.api.transfer.service;

import com.stagepass.api.transfer.dto.TransferCreateRequest;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.transfer.Transfer;
import com.stagepass.domain.transfer.TransferRepository;
import com.stagepass.domain.transfer.TransferStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.TransferRedisRepository;
import com.stagepass.kafka.event.NotificationEvent;
import com.stagepass.kafka.event.TransferEvent;
import com.stagepass.kafka.producer.EventPublisher;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

  @InjectMocks private TransferService transferService;

  @Mock private TransferRepository transferRepository;
  @Mock private ReservationRepository reservationRepository;
  @Mock private UserRepository userRepository;
  @Mock private TransferRedisRepository transferRedisRepository;
  @Mock private EventPublisher eventPublisher;

  private static final Long FROM_USER_ID = 1L;
  private static final Long TO_USER_ID = 2L;
  private static final Long RESERVATION_ID = 10L;
  private static final Long TRANSFER_ID = 20L;

  private User fromUser;
  private User toUser;
  private Show futureShow;
  private Reservation confirmedReservation;

  @BeforeEach
  void setUp() {
    fromUser = User.builder()
        .email("from@test.com").passwordHash("hash").name("양도자").role(UserRole.USER).build();
    ReflectionTestUtils.setField(fromUser, "id", FROM_USER_ID);

    toUser = User.builder()
        .email("to@test.com").passwordHash("hash").name("양수자").role(UserRole.USER).build();
    ReflectionTestUtils.setField(toUser, "id", TO_USER_ID);

    Performance performance = Performance.builder().title("테스트 공연").build();
    ReflectionTestUtils.setField(performance, "id", 1L);

    futureShow = Show.builder()
        .performance(performance)
        .showDatetime(LocalDateTime.now().plusDays(7))
        .totalSeats(100)
        .status(ShowStatus.ON_SALE)
        .build();
    ReflectionTestUtils.setField(futureShow, "id", 100L);

    confirmedReservation = Reservation.builder()
        .user(fromUser).show(futureShow).totalPrice(50000).build();
    ReflectionTestUtils.setField(confirmedReservation, "id", RESERVATION_ID);
    ReflectionTestUtils.setField(confirmedReservation, "status", ReservationStatus.CONFIRMED);
  }

  @Test
  @DisplayName("양도 등록 — CONFIRMED 예매만 양도 가능")
  void create_미결제예매_실패() {
    Reservation pending = Reservation.builder()
        .user(fromUser).show(futureShow).totalPrice(50000).build();
    ReflectionTestUtils.setField(pending, "id", RESERVATION_ID);

    TransferCreateRequest request = new TransferCreateRequest();
    ReflectionTestUtils.setField(request, "reservationId", RESERVATION_ID);

    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, FROM_USER_ID))
        .willReturn(Optional.of(pending));

    assertThatThrownBy(() -> transferService.create(FROM_USER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.RESERVATION_NOT_CONFIRMED.getMessage());
  }

  @Test
  @DisplayName("양도 등록 — 이미 OPEN 양도글 존재 시 실패")
  void create_중복양도_실패() {
    TransferCreateRequest request = new TransferCreateRequest();
    ReflectionTestUtils.setField(request, "reservationId", RESERVATION_ID);

    given(reservationRepository.findByIdAndUserId(RESERVATION_ID, FROM_USER_ID))
        .willReturn(Optional.of(confirmedReservation));
    given(transferRepository.existsByReservationIdAndStatus(RESERVATION_ID, TransferStatus.OPEN))
        .willReturn(true);

    assertThatThrownBy(() -> transferService.create(FROM_USER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.TRANSFER_ALREADY_EXISTS.getMessage());
  }

  @Test
  @DisplayName("양도 수락 — Redis 락 획득 실패 시 TRANSFER_ALREADY_CLAIMED 예외")
  void claim_동시수락_실패() {
    Transfer transfer = Transfer.builder()
        .reservation(confirmedReservation).fromUser(fromUser)
        .expiresAt(futureShow.getShowDatetime()).build();
    ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);

    given(transferRepository.findById(TRANSFER_ID)).willReturn(Optional.of(transfer));
    given(transferRedisRepository.claim(TRANSFER_ID, TO_USER_ID)).willReturn(false);

    assertThatThrownBy(() -> transferService.claim(TRANSFER_ID, TO_USER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.TRANSFER_ALREADY_CLAIMED.getMessage());

    then(eventPublisher).should(never()).publishTransferClaimed(any());
  }

  @Test
  @DisplayName("양도 수락 — 본인 양도글은 수락 불가")
  void claim_본인수락_실패() {
    Transfer transfer = Transfer.builder()
        .reservation(confirmedReservation).fromUser(fromUser)
        .expiresAt(futureShow.getShowDatetime()).build();
    ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);

    given(transferRepository.findById(TRANSFER_ID)).willReturn(Optional.of(transfer));

    assertThatThrownBy(() -> transferService.claim(TRANSFER_ID, FROM_USER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.TRANSFER_SELF_CLAIM.getMessage());
  }

  @Test
  @DisplayName("양도 수락 — 성공 시 이벤트 발행 및 예매 소유권 이전")
  void claim_성공() {
    Transfer transfer = Transfer.builder()
        .reservation(confirmedReservation).fromUser(fromUser)
        .expiresAt(futureShow.getShowDatetime()).build();
    ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);

    given(transferRepository.findById(TRANSFER_ID)).willReturn(Optional.of(transfer));
    given(transferRedisRepository.claim(TRANSFER_ID, TO_USER_ID)).willReturn(true);
    given(userRepository.findById(TO_USER_ID)).willReturn(Optional.of(toUser));

    transferService.claim(TRANSFER_ID, TO_USER_ID);

    then(eventPublisher).should().publishTransferClaimed(any(TransferEvent.class));
    then(eventPublisher).should().publishNotification(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("양도 수락 — 락 획득 후 예외 발생 시 Redis 락 해제")
  void claim_예외시_락해제() {
    Transfer transfer = Transfer.builder()
        .reservation(confirmedReservation).fromUser(fromUser)
        .expiresAt(futureShow.getShowDatetime()).build();
    ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);

    given(transferRepository.findById(TRANSFER_ID)).willReturn(Optional.of(transfer));
    given(transferRedisRepository.claim(TRANSFER_ID, TO_USER_ID)).willReturn(true);
    given(userRepository.findById(TO_USER_ID))
        .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

    assertThatThrownBy(() -> transferService.claim(TRANSFER_ID, TO_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);

    then(transferRedisRepository).should().release(TRANSFER_ID);
    then(eventPublisher).should(never()).publishTransferClaimed(any());
  }

  @Test
  @DisplayName("양도 취소 — OPEN 상태가 아니면 실패")
  void cancel_OPEN아님_실패() {
    Transfer transfer = Transfer.builder()
        .reservation(confirmedReservation).fromUser(fromUser)
        .expiresAt(futureShow.getShowDatetime()).build();
    ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);
    ReflectionTestUtils.setField(transfer, "status", TransferStatus.CLAIMED);

    given(transferRepository.findByIdAndFromUserId(TRANSFER_ID, FROM_USER_ID))
        .willReturn(Optional.of(transfer));

    assertThatThrownBy(() -> transferService.cancel(TRANSFER_ID, FROM_USER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.TRANSFER_NOT_OPEN.getMessage());
  }
}
