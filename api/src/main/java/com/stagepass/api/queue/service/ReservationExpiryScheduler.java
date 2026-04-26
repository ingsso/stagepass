package com.stagepass.api.queue.service;

import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.SeatHoldEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

  private final ReservationRepository reservationRepository;
  private final ReservationSeatRepository reservationSeatRepository;
  private final SeatRedisRepository seatRedisRepository;
  private final EventPublisher eventPublisher;
  private final WaitlistService waitlistService;
  private final QueueService queueService;
  private final QueueEntryRepository queueEntryRepository;

  private static final int BATCH_SIZE = 100;

  // 1분마다 만료된 PENDING 예매를 100건씩 배치 처리 — 단일 대형 트랜잭션 방지
  @Scheduled(fixedDelay = 60_000)
  public void expireReservations() {
    List<Reservation> batch;
    int totalProcessed = 0;

    do {
      batch = processNextBatch();
      totalProcessed += batch.size();
    } while (batch.size() == BATCH_SIZE); // 조회 건수가 배치 크기와 같으면 다음 배치 존재

    if (totalProcessed > 0) {
      log.info("[Scheduler] 예매 만료 처리 완료 total={}", totalProcessed);
    }
  }

  // 각 배치를 별도 트랜잭션으로 처리 — 실패 시 해당 배치만 롤백
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
          .ifPresent(e -> queueService.activateNextBatch(showId));

      waitlistService.notifyNext(showId);

      log.info("[Scheduler] 예매 만료 처리 reservationId={}", reservation.getId());
    }

    return expired;
  }
}