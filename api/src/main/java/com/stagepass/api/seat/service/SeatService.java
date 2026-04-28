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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stagepass.domain.reservation.ReservationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
  private final CacheManager cacheManager;

  // 회차별 좌석 목록 조회 — N+1 제거(JOIN FETCH) + Redis TTL Pipeline + 캐싱(TTL 10s)
  @Cacheable(value = "seat-list", key = "#showId")
  @Transactional(readOnly = true)
  public List<SeatResponse> getSeats(Long showId) {
    List<Seat> seats = seatRepository.findByShowIdWithZone(showId);

    List<Long> seatIds = seats.stream().map(Seat::getId).toList();
    Map<Long, Long> ttlMap = seatRedisRepository.getBulkRemainingTtl(seatIds);

    return seats.stream()
        .map(seat -> {
          long ttl = ttlMap.getOrDefault(seat.getId(), 0L);
          return new SeatResponse(seat, ttl > 0 ? ttl : null);
        })
        .toList();
  }

  // 좌석 선점 — Redis SET NX PX + 캐시 무효화
  @CacheEvict(value = "seat-list", key = "#showId")
  @Transactional
  public SeatHoldResponse holdSeats(Long showId, Long userId, SeatHoldRequest request) {
    // 동일 회차 PENDING 예매 중복 선점 방지
    if (reservationRepository.existsByUserIdAndShowIdAndStatus(userId, showId, ReservationStatus.PENDING)) {
      throw new BusinessException(ErrorCode.DUPLICATE_SEAT_HOLD);
    }

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

    // 선점된 좌석 일괄 조회 (zone JOIN FETCH — N+1 방지)
    List<Seat> seats = seatRepository.findAllByIdWithZone(heldIds);
    if (seats.size() != heldIds.size()) {
      throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
    }

    int totalPrice = seats.stream()
        .mapToInt(seat -> seat.getZone().getPrice())
        .sum();

    Reservation reservation = Reservation.builder()
        .user(user)
        .show(show)
        .totalPrice(totalPrice)
        .build();
    reservationRepository.save(reservation);

    // ReservationSeat 일괄 저장
    List<ReservationSeat> reservationSeats = seats.stream()
        .map(seat -> ReservationSeat.builder()
            .reservation(reservation)
            .seat(seat)
            .build())
        .toList();
    reservationSeatRepository.saveAll(reservationSeats);

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

  // 선점 해제 + 해당 showId 캐시만 무효화 (allEntries 방지)
  @Transactional
  public void releaseSeats(Long reservationId, Long userId) {
    Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

    reservationSeatRepository.findByReservationId(reservationId)
        .forEach(rs -> seatRedisRepository.release(rs.getSeat().getId(), userId));

    Long showId = reservation.getShow().getId();
    reservation.expire();

    Cache seatListCache = cacheManager.getCache("seat-list");
    if (seatListCache != null) seatListCache.evict(showId);

    log.info("[Seat] 선점 해제 reservationId={} userId={}", reservationId, userId);
  }
}