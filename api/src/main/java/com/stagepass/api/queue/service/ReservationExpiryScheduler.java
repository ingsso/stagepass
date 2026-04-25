package com.stagepass.api.queue.service;

import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.SeatHoldEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  // 1분마다 만료된 PENDING 예매 정리
  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void expireReservations() {
    List<Reservation> expired =
        reservationRepository.findExpiredReservations(LocalDateTime.now());

    if (expired.isEmpty()) return;

    for (Reservation reservation : expired) {
      reservation.expire();

      reservationSeatRepository.findByReservationIdWithSeat(reservation.getId())
          .forEach(rs -> {
            seatRedisRepository.release(
                rs.getSeat().getId(),
                reservation.getUser().getId()
            );
            eventPublisher.publishSeatHoldExpired(
                new SeatHoldEvent(
                    rs.getSeat().getId(),
                    reservation.getUser().getId(),
                    reservation.getId(),
                    System.currentTimeMillis()
                )
            );
          });

      log.info("[Scheduler] 예매 만료 처리 reservationId={}", reservation.getId());

      // 선점이 풀린 좌석이 있으므로 취소 대기 첫 번째 대기자에게 알림
      waitlistService.notifyNext(reservation.getShow().getId());
    }
  }
}