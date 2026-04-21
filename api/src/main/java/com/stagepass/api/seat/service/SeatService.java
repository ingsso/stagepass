package com.stagepass.api.seat.service;

import com.stagepass.api.seat.dto.*;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.*;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.domain.reservation.ReservationSeat;
import com.stagepass.domain.reservation.ReservationSeatRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.infra.redis.SeatRedisRepository;
import com.stagepass.kafka.event.SeatHoldEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

  private final SeatRepository seatRepository;
  private final ZoneRepository zoneRepository;
  private final ShowRepository showRepository;
  private final ReservationRepository reservationRepository;
  private final ReservationSeatRepository reservationSeatRepository;
  private final UserRepository userRepository;
  private final SeatRedisRepository seatRedisRepository;
  private final EventPublisher eventPublisher;

  // 회차별 좌석 목록 조회 (Redis TTL 포함)
  @Transactional(readOnly = true)
  public List<SeatResponse> getSeats(Long showId) {
    List<Zone> zones = zoneRepository.findByShowId(showId);
    List<SeatResponse> result = new ArrayList<>();

    for (Zone zone : zones) {
      for (Seat seat : seatRepository.findByZoneId(zone.getId())) {
        long ttl = seatRedisRepository.getRemainingTtl(seat.getId());
        result.add(new SeatResponse(seat, ttl > 0 ? ttl : null));
      }
    }
    return result;
  }

  // 좌석 선점 — Redis SET NX PX
  @Transactional
  public SeatHoldResponse holdSeats(Long showId, Long userId, SeatHoldRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    Show show = showRepository.findById(showId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOW_NOT_FOUND));

    List<Long> heldIds = new ArrayList<>();
    List<Long> failedIds = new ArrayList<>();

    for (Long seatId : request.getSeatIds()) {
      boolean success = seatRedisRepository.hold(seatId, userId);
      if (success) {
        heldIds.add(seatId);
      } else {
        failedIds.add(seatId);
      }
    }

    // 일부라도 실패하면 선점한 것도 전부 롤백
    if (!failedIds.isEmpty()) {
      heldIds.forEach(seatId -> seatRedisRepository.release(seatId, userId));
      throw new BusinessException(ErrorCode.SEAT_ALREADY_HELD);
    }

    // 예매 레코드 생성
    int totalPrice = heldIds.stream()
        .map(seatId -> seatRepository.findById(seatId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND)))
        .mapToInt(seat -> seat.getZone().getPrice())
        .sum();

    Reservation reservation = Reservation.builder()
        .user(user)
        .show(show)
        .totalPrice(totalPrice)
        .build();
    reservationRepository.save(reservation);

    // ReservationSeat 생성
    for (Long seatId : heldIds) {
      Seat seat = seatRepository.findById(seatId)
          .orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
      reservationSeatRepository.save(
          ReservationSeat.builder()
              .reservation(reservation)
              .seat(seat)
              .build()
      );
    }

    // Kafka 이벤트 발행
    long expiresAt = System.currentTimeMillis() + 300_000L;
    heldIds.forEach(seatId ->
        eventPublisher.publishSeatHold(
            new SeatHoldEvent(seatId, userId, reservation.getId(), expiresAt)
        )
    );

    log.info("[Seat] 선점 완료 userId={} seatIds={} reservationId={}",
        userId, heldIds, reservation.getId());

    return new SeatHoldResponse(heldIds, failedIds, reservation.getId(), expiresAt);
  }

  // 선점 해제
  @Transactional
  public void releaseSeats(Long reservationId, Long userId) {
    Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    reservationSeatRepository.findByReservationId(reservationId)
        .forEach(rs -> seatRedisRepository.release(rs.getSeat().getId(), userId));

    reservation.expire();
    log.info("[Seat] 선점 해제 reservationId={} userId={}", reservationId, userId);
  }
}