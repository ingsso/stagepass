package com.stagepass.api.exchange.service;

import com.stagepass.api.exchange.dto.ExchangeRequest;
import com.stagepass.api.exchange.dto.ExchangeResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.exchange.SeatExchange;
import com.stagepass.domain.exchange.SeatExchangeRepository;
import com.stagepass.domain.exchange.SeatExchangeStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.kafka.event.SeatExchangeEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatExchangeService {

  private final SeatExchangeRepository exchangeRepository;
  private final ReservationRepository reservationRepository;
  private final UserRepository userRepository;
  private final EventPublisher eventPublisher;

  // 교환 제안
  @Transactional
  public ExchangeResponse propose(Long proposerId, ExchangeRequest request) {
    User proposer = userRepository.findById(proposerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    Reservation myRes = reservationRepository.findByIdAndUserId(
            request.getMyReservationId(), proposerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    Reservation targetRes = reservationRepository.findById(request.getTargetReservationId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // 본인에게 제안 불가
    if (proposerId.equals(targetRes.getUser().getId())) {
      throw new BusinessException(ErrorCode.EXCHANGE_SELF_PROPOSE);
    }

    // 같은 회차만 교환 가능
    if (!myRes.getShow().getId().equals(targetRes.getShow().getId())) {
      throw new BusinessException(ErrorCode.EXCHANGE_SHOW_MISMATCH);
    }

    // 확정된 예매만 교환 가능
    if (myRes.getStatus() != ReservationStatus.CONFIRMED
        || targetRes.getStatus() != ReservationStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.RESERVATION_NOT_CONFIRMED);
    }

    // 이미 진행 중인 교환 제안이 있으면 불가
    if (exchangeRepository.existsByProposerReservationIdAndStatus(myRes.getId(), SeatExchangeStatus.PENDING)
        || exchangeRepository.existsByReceiverReservationIdAndStatus(targetRes.getId(), SeatExchangeStatus.PENDING)) {
      throw new BusinessException(ErrorCode.EXCHANGE_ALREADY_PENDING);
    }

    SeatExchange exchange = SeatExchange.builder()
        .proposer(proposer)
        .receiver(targetRes.getUser())
        .proposerReservation(myRes)
        .receiverReservation(targetRes)
        .build();

    exchangeRepository.save(exchange);
    log.info("[Exchange] 교환 제안 exchangeId={} proposer={} receiver={}",
        exchange.getId(), proposerId, targetRes.getUser().getId());

    return new ExchangeResponse(exchangeRepository.findByIdWithDetails(exchange.getId()).orElseThrow());
  }

  // 내가 받은 교환 제안 목록
  @Transactional(readOnly = true)
  public List<ExchangeResponse> getReceivedProposals(Long userId) {
    return exchangeRepository.findByReceiverIdAndStatusWithDetails(userId, SeatExchangeStatus.PENDING)
        .stream()
        .map(ExchangeResponse::new)
        .toList();
  }

  // 내가 보낸 교환 제안 목록
  @Transactional(readOnly = true)
  public List<ExchangeResponse> getSentProposals(Long userId) {
    return exchangeRepository.findByProposerIdWithDetails(userId)
        .stream()
        .map(ExchangeResponse::new)
        .toList();
  }

  // 교환 수락 — 비관적 락으로 두 예매를 원자적으로 스왑
  @Transactional
  public ExchangeResponse accept(Long exchangeId, Long receiverId) {
    SeatExchange exchange = exchangeRepository.findByIdWithDetails(exchangeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

    if (!exchange.getReceiver().getId().equals(receiverId)) {
      throw new BusinessException(ErrorCode.EXCHANGE_FORBIDDEN);
    }
    if (exchange.getStatus() != SeatExchangeStatus.PENDING) {
      throw new BusinessException(ErrorCode.EXCHANGE_NOT_PENDING);
    }

    // 비관적 락으로 두 예매 조회 (데드락 방지: 작은 ID 먼저 락)
    Long id1 = Math.min(exchange.getProposerReservation().getId(), exchange.getReceiverReservation().getId());
    Long id2 = Math.max(exchange.getProposerReservation().getId(), exchange.getReceiverReservation().getId());

    Reservation res1 = reservationRepository.findByIdWithLock(id1)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    Reservation res2 = reservationRepository.findByIdWithLock(id2)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    // 소유자 스왑
    User proposer = exchange.getProposer();
    User receiver = exchange.getReceiver();
    res1.transferTo(res1.getUser().getId().equals(proposer.getId()) ? receiver : proposer);
    res2.transferTo(res2.getUser().getId().equals(receiver.getId()) ? proposer : receiver);

    exchange.accept();

    eventPublisher.publishExchangeCompleted(
        new SeatExchangeEvent(exchangeId, proposer.getId(), receiverId));

    log.info("[Exchange] 교환 완료 exchangeId={} proposer={} receiver={}",
        exchangeId, proposer.getId(), receiverId);

    return new ExchangeResponse(exchange);
  }

  // 교환 거절
  @Transactional
  public ExchangeResponse reject(Long exchangeId, Long receiverId) {
    SeatExchange exchange = exchangeRepository.findById(exchangeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

    if (!exchange.getReceiver().getId().equals(receiverId)) {
      throw new BusinessException(ErrorCode.EXCHANGE_FORBIDDEN);
    }
    if (exchange.getStatus() != SeatExchangeStatus.PENDING) {
      throw new BusinessException(ErrorCode.EXCHANGE_NOT_PENDING);
    }

    exchange.reject();
    log.info("[Exchange] 교환 거절 exchangeId={}", exchangeId);
    return new ExchangeResponse(exchangeRepository.findByIdWithDetails(exchangeId).orElseThrow());
  }

  // 교환 제안 취소 (제안자만)
  @Transactional
  public void cancel(Long exchangeId, Long proposerId) {
    SeatExchange exchange = exchangeRepository.findById(exchangeId)
        .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND));

    if (!exchange.getProposer().getId().equals(proposerId)) {
      throw new BusinessException(ErrorCode.EXCHANGE_FORBIDDEN);
    }
    if (exchange.getStatus() != SeatExchangeStatus.PENDING) {
      throw new BusinessException(ErrorCode.EXCHANGE_NOT_PENDING);
    }

    exchange.cancel();
    log.info("[Exchange] 교환 취소 exchangeId={}", exchangeId);
  }
}
