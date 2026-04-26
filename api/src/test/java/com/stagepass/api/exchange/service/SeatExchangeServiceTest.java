package com.stagepass.api.exchange.service;

import com.stagepass.api.exchange.dto.ExchangeRequest;
import com.stagepass.api.exchange.dto.ExchangeResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.exchange.SeatExchange;
import com.stagepass.domain.exchange.SeatExchangeRepository;
import com.stagepass.domain.exchange.SeatExchangeStatus;
import static com.stagepass.domain.exchange.SeatExchangeStatus.*;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.kafka.event.SeatExchangeEvent;
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

@ExtendWith(MockitoExtension.class)
class SeatExchangeServiceTest {

  @InjectMocks private SeatExchangeService exchangeService;

  @Mock private SeatExchangeRepository exchangeRepository;
  @Mock private ReservationRepository reservationRepository;
  @Mock private UserRepository userRepository;
  @Mock private EventPublisher eventPublisher;

  private static final Long PROPOSER_ID = 1L;
  private static final Long RECEIVER_ID = 2L;
  private static final Long MY_RES_ID = 10L;
  private static final Long TARGET_RES_ID = 20L;
  private static final Long SHOW_ID = 100L;

  private User proposer;
  private User receiver;
  private Show show;
  private Reservation myReservation;
  private Reservation targetReservation;

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

    proposer = User.builder().email("proposer@test.com").passwordHash("hash").name("제안자").role(UserRole.USER).build();
    ReflectionTestUtils.setField(proposer, "id", PROPOSER_ID);

    receiver = User.builder().email("receiver@test.com").passwordHash("hash").name("수락자").role(UserRole.USER).build();
    ReflectionTestUtils.setField(receiver, "id", RECEIVER_ID);

    myReservation = Reservation.builder().user(proposer).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(myReservation, "id", MY_RES_ID);
    ReflectionTestUtils.setField(myReservation, "status", ReservationStatus.CONFIRMED);

    targetReservation = Reservation.builder().user(receiver).show(show).totalPrice(50000).build();
    ReflectionTestUtils.setField(targetReservation, "id", TARGET_RES_ID);
    ReflectionTestUtils.setField(targetReservation, "status", ReservationStatus.CONFIRMED);
  }

  @Test
  @DisplayName("교환 제안 — 정상 케이스")
  void propose_성공() {
    ExchangeRequest request = new ExchangeRequest();
    ReflectionTestUtils.setField(request, "myReservationId", MY_RES_ID);
    ReflectionTestUtils.setField(request, "targetReservationId", TARGET_RES_ID);

    given(userRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
    given(reservationRepository.findByIdAndUserId(MY_RES_ID, PROPOSER_ID)).willReturn(Optional.of(myReservation));
    given(reservationRepository.findById(TARGET_RES_ID)).willReturn(Optional.of(targetReservation));
    given(exchangeRepository.existsByProposerReservationIdAndStatus(MY_RES_ID, SeatExchangeStatus.PENDING)).willReturn(false);
    given(exchangeRepository.existsByReceiverReservationIdAndStatus(TARGET_RES_ID, SeatExchangeStatus.PENDING)).willReturn(false);

    SeatExchange saved = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    given(exchangeRepository.findByIdWithDetails(any())).willReturn(Optional.of(saved));

    ExchangeResponse response = exchangeService.propose(PROPOSER_ID, request);

    assertThat(response.getProposerId()).isEqualTo(PROPOSER_ID);
    assertThat(response.getReceiverId()).isEqualTo(RECEIVER_ID);
    assertThat(response.getStatus()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("본인에게 교환 제안 시 EXCHANGE_SELF_PROPOSE 예외")
  void propose_자기자신_실패() {
    ExchangeRequest request = new ExchangeRequest();
    ReflectionTestUtils.setField(request, "myReservationId", MY_RES_ID);
    ReflectionTestUtils.setField(request, "targetReservationId", TARGET_RES_ID);

    // targetReservation도 proposer 소유로 설정
    ReflectionTestUtils.setField(targetReservation, "user", proposer);

    given(userRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
    given(reservationRepository.findByIdAndUserId(MY_RES_ID, PROPOSER_ID)).willReturn(Optional.of(myReservation));
    given(reservationRepository.findById(TARGET_RES_ID)).willReturn(Optional.of(targetReservation));

    assertThatThrownBy(() -> exchangeService.propose(PROPOSER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_SELF_PROPOSE.getMessage());
  }

  @Test
  @DisplayName("다른 회차 예매끼리 제안 시 EXCHANGE_SHOW_MISMATCH 예외")
  void propose_다른회차_실패() {
    Show otherShow = Show.builder()
        .performance(show.getPerformance())
        .showDatetime(LocalDateTime.now().plusDays(2))
        .totalSeats(100).status(ShowStatus.ON_SALE).build();
    ReflectionTestUtils.setField(otherShow, "id", 999L);
    ReflectionTestUtils.setField(targetReservation, "show", otherShow);

    ExchangeRequest request = new ExchangeRequest();
    ReflectionTestUtils.setField(request, "myReservationId", MY_RES_ID);
    ReflectionTestUtils.setField(request, "targetReservationId", TARGET_RES_ID);

    given(userRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
    given(reservationRepository.findByIdAndUserId(MY_RES_ID, PROPOSER_ID)).willReturn(Optional.of(myReservation));
    given(reservationRepository.findById(TARGET_RES_ID)).willReturn(Optional.of(targetReservation));

    assertThatThrownBy(() -> exchangeService.propose(PROPOSER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_SHOW_MISMATCH.getMessage());
  }

  @Test
  @DisplayName("이미 PENDING 제안 있으면 EXCHANGE_ALREADY_PENDING 예외")
  void propose_중복_실패() {
    ExchangeRequest request = new ExchangeRequest();
    ReflectionTestUtils.setField(request, "myReservationId", MY_RES_ID);
    ReflectionTestUtils.setField(request, "targetReservationId", TARGET_RES_ID);

    given(userRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
    given(reservationRepository.findByIdAndUserId(MY_RES_ID, PROPOSER_ID)).willReturn(Optional.of(myReservation));
    given(reservationRepository.findById(TARGET_RES_ID)).willReturn(Optional.of(targetReservation));
    given(exchangeRepository.existsByProposerReservationIdAndStatus(MY_RES_ID, SeatExchangeStatus.PENDING)).willReturn(true);

    assertThatThrownBy(() -> exchangeService.propose(PROPOSER_ID, request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_ALREADY_PENDING.getMessage());
  }

  @Test
  @DisplayName("교환 거절 — 상태가 REJECTED로 변경된다")
  void reject_성공() {
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    ReflectionTestUtils.setField(exchange, "id", 1L);
    given(exchangeRepository.findById(1L)).willReturn(Optional.of(exchange));
    given(exchangeRepository.findByIdWithDetails(1L)).willReturn(Optional.of(exchange));

    ExchangeResponse response = exchangeService.reject(1L, RECEIVER_ID);

    assertThat(response.getStatus()).isEqualTo("REJECTED");
  }

  @Test
  @DisplayName("교환 거절 — 수락자가 아닌 사람이 거절하면 EXCHANGE_FORBIDDEN 예외")
  void reject_권한없음_실패() {
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    ReflectionTestUtils.setField(exchange, "id", 1L);
    given(exchangeRepository.findById(1L)).willReturn(Optional.of(exchange));

    assertThatThrownBy(() -> exchangeService.reject(1L, PROPOSER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_FORBIDDEN.getMessage());
  }

  @Test
  @DisplayName("도메인 가드 — REJECTED 상태에서 accept() 호출 시 EXCHANGE_NOT_PENDING 예외")
  void domain_이미거절된교환_accept_실패() {
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    exchange.reject(); // REJECTED 상태로 전환

    assertThatThrownBy(exchange::accept)
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_NOT_PENDING.getMessage());
  }

  @Test
  @DisplayName("도메인 가드 — ACCEPTED 상태에서 cancel() 호출 시 EXCHANGE_NOT_PENDING 예외")
  void domain_이미수락된교환_cancel_실패() {
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    // accept()는 PENDING 상태에서만 호출 가능 — 직접 상태 설정으로 시뮬레이션
    ReflectionTestUtils.setField(exchange, "status", SeatExchangeStatus.ACCEPTED);

    assertThatThrownBy(exchange::cancel)
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_NOT_PENDING.getMessage());
  }

  @Test
  @DisplayName("도메인 가드 — expire()는 PENDING이 아닌 상태에서 멱등 처리된다")
  void domain_이미완료된교환_expire_멱등() {
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    exchange.reject(); // REJECTED 상태

    exchange.expire();

    assertThat(exchange.getStatus()).isEqualTo(SeatExchangeStatus.REJECTED);
  }

  @Test
  @DisplayName("교환 수락 — 성공 시 소유권 스왑 + 이벤트 발행")
  void accept_성공_이벤트발행() {
    Long exchangeId = 1L;
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    ReflectionTestUtils.setField(exchange, "id", exchangeId);

    given(exchangeRepository.findByIdWithDetails(exchangeId)).willReturn(Optional.of(exchange));
    given(reservationRepository.findByIdWithLock(MY_RES_ID)).willReturn(Optional.of(myReservation));
    given(reservationRepository.findByIdWithLock(TARGET_RES_ID)).willReturn(Optional.of(targetReservation));

    exchangeService.accept(exchangeId, RECEIVER_ID);

    ArgumentCaptor<SeatExchangeEvent> captor = ArgumentCaptor.forClass(SeatExchangeEvent.class);
    then(eventPublisher).should().publishExchangeCompleted(captor.capture());
    assertThat(captor.getValue().getProposerId()).isEqualTo(PROPOSER_ID);
    assertThat(captor.getValue().getReceiverId()).isEqualTo(RECEIVER_ID);
    assertThat(exchange.getStatus()).isEqualTo(ACCEPTED);
  }

  @Test
  @DisplayName("교환 수락 — receiver가 아닌 사람이 수락하면 EXCHANGE_FORBIDDEN 예외")
  void accept_권한없음_실패() {
    Long exchangeId = 1L;
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    ReflectionTestUtils.setField(exchange, "id", exchangeId);

    given(exchangeRepository.findByIdWithDetails(exchangeId)).willReturn(Optional.of(exchange));

    assertThatThrownBy(() -> exchangeService.accept(exchangeId, PROPOSER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_FORBIDDEN.getMessage());

    then(eventPublisher).should(org.mockito.Mockito.never()).publishExchangeCompleted(any());
  }

  @Test
  @DisplayName("교환 수락 — 이미 ACCEPTED 상태면 EXCHANGE_NOT_PENDING 예외")
  void accept_이미수락됨_실패() {
    Long exchangeId = 1L;
    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer).receiver(receiver)
        .proposerReservation(myReservation).receiverReservation(targetReservation)
        .build();
    ReflectionTestUtils.setField(exchange, "id", exchangeId);
    ReflectionTestUtils.setField(exchange, "status", ACCEPTED);

    given(exchangeRepository.findByIdWithDetails(exchangeId)).willReturn(Optional.of(exchange));

    assertThatThrownBy(() -> exchangeService.accept(exchangeId, RECEIVER_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.EXCHANGE_NOT_PENDING.getMessage());
  }
}
