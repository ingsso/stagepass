package com.stagepass.api.reservation.service;

import com.stagepass.api.queue.service.QueueService;
import com.stagepass.api.reservation.dto.ReservationResponse;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.domain.reservation.ReservationStatus;
import com.stagepass.infra.redis.SeatRedisRepository;
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
  private final QueueService queueService;
  private final QueueEntryRepository queueEntryRepository;

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

    // Redis 선점 해제 — JOIN FETCH로 Seat lazy 로딩 N+1 방지
    reservationSeatRepository.findByReservationIdWithSeat(reservationId)
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

    // ACTIVATED 상태 큐 슬롯이 반환되면 다음 배치 활성화
    queueEntryRepository.findByShowIdAndUserId(showId, userId)
        .filter(e -> e.getStatus() == QueueStatus.ACTIVATED)
        .ifPresent(e -> queueService.activateNextBatch(showId));

    log.info("[Reservation] cancelled reservationId={} userId={}", reservationId, userId);
  }
}