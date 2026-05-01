package com.stagepass.api.queue.service;

import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.NotificationEvent;
import com.stagepass.kafka.event.SeatHoldEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryBatchProcessor {

  private final ReservationRepository reservationRepository;
  private final ReservationSeatRepository reservationSeatRepository;
  private final SeatRedisRepository seatRedisRepository;
  private final EventPublisher eventPublisher;
  private final WaitlistService waitlistService;
  private final QueueService queueService;
  private final QueueEntryRepository queueEntryRepository;

  static final int BATCH_SIZE = 100;

  // 별도 빈으로 분리 — ReservationExpiryScheduler의 자기호출 문제 해결 (@Transactional AOP 적용)
  @Transactional
  public List<Reservation> processNextBatch() {
    List<Reservation> expired = reservationRepository.findExpiredReservations(
        LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

    for (Reservation reservation : expired) {
      reservation.expire();

      reservationSeatRepository.findByReservationIdWithSeat(reservation.getId())
          .forEach(rs -> {
            seatRedisRepository.release(rs.getSeat().getId(), reservation.getUser().getId());
            eventPublisher.publishSeatHoldExpired(
                new SeatHoldEvent(
                    rs.getSeat().getId(),
                    reservation.getUser().getId(),
                    reservation.getId(),
                    System.currentTimeMillis()
                )
            );
          });

      Long showId = reservation.getShow().getId();
      Long userId = reservation.getUser().getId();

      queueEntryRepository.findByShowIdAndUserId(showId, userId)
          .filter(e -> e.getStatus() == QueueStatus.ACTIVATED)
          .ifPresent(e -> {
            queueEntryRepository.delete(e);
            queueService.activateNextBatch(showId);
          });

      waitlistService.notifyNext(showId);

      eventPublisher.publishNotification(new NotificationEvent(
          userId, "RESERVATION_EXPIRED", "예매 시간이 만료되었습니다. 좌석 선점이 해제됩니다."
      ));

      log.info("[Scheduler] 예매 만료 처리 reservationId={}", reservation.getId());
    }

    return expired;
  }
}
