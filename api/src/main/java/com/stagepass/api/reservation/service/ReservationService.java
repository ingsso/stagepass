package com.stagepass.api.reservation.service;

import com.stagepass.api.reservation.dto.ReservationResponse;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.kafka.event.PaymentCancelEvent;
import com.stagepass.kafka.event.ReservationEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final ReservationSeatRepository reservationSeatRepository;
  private final SeatRedisRepository seatRedisRepository;
  private final EventPublisher eventPublisher;
  private final WaitlistService waitlistService;

  // 내 예매 목록
  @Transactional(readOnly = true)
  public List<ReservationResponse> getMyReservations(Long userId) {
    return reservationRepository.findByUserId(userId).stream()
        .map(ReservationResponse::new)
        .toList();
  }

  // 예매 상세
  @Transactional(readOnly = true)
  public ReservationResponse getOne(Long reservationId, Long userId) {
    Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    return new ReservationResponse(reservation);
  }

  // 예매 취소
  @Transactional
  public void cancel(Long reservationId, Long userId) {
    Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    boolean wasConfirmed = reservation.getStatus() == ReservationStatus.CONFIRMED;

    // Redis 선점 해제
    reservationSeatRepository.findByReservationId(reservationId)
        .forEach(rs -> seatRedisRepository.release(rs.getSeat().getId(), userId));

    reservation.cancel();

    Long showId = reservation.getShow().getId();

    // 결제가 완료된 예매라면 환불 요청 이벤트 발행 → payment 모듈이 토스 취소 처리
    if (wasConfirmed) {
      eventPublisher.publishPaymentCancelRequested(new PaymentCancelEvent(reservationId, userId));
    }

    // Kafka 취소 이벤트 발행 → 알림 Consumer 처리
    eventPublisher.publishReservationCancelled(new ReservationEvent(reservationId, userId, showId));

    // 취소 대기 첫 번째 대기자에게 알림
    waitlistService.notifyNext(showId);

    log.info("[Reservation] 예매 취소 reservationId={} userId={}", reservationId, userId);
  }
}