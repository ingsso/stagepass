package com.stagepass.domain.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  // 내 예매 목록 — Show/Performance/ReservationSeats/Seat 한 번에 페치 (N+1 방지)
  @Query("""
      SELECT DISTINCT r FROM Reservation r
      JOIN FETCH r.show s
      JOIN FETCH s.performance
      LEFT JOIN FETCH r.reservationSeats rs
      LEFT JOIN FETCH rs.seat
      WHERE r.user.id = :userId
      ORDER BY r.reservedAt DESC
      """)
  List<Reservation> findByUserId(@Param("userId") Long userId);

  List<Reservation> findByShowId(Long showId);

  // 만료 처리 대상 배치 조회 (스케줄러용) — Pageable로 청크 단위 처리
  @Query("SELECT r FROM Reservation r WHERE r.status = 'PENDING' AND r.expiresAt < :now")
  List<Reservation> findExpiredReservations(@Param("now") LocalDateTime now, Pageable pageable);

  Optional<Reservation> findByIdAndUserId(Long id, Long userId);

  // 교환용 비관적 락 조회
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Reservation r WHERE r.id = :id")
  Optional<Reservation> findByIdWithLock(@Param("id") Long id);

  // 대시보드 단일 집계 쿼리 — 6회 → 1회 (Object[]: total, confirmed, cancelled, revenue, todayCount, todayRevenue)
  @Query("""
      SELECT
        COUNT(r),
        SUM(CASE WHEN r.status = :confirmed THEN 1 ELSE 0 END),
        SUM(CASE WHEN r.status = :cancelled THEN 1 ELSE 0 END),
        COALESCE(SUM(CASE WHEN r.status = :confirmed THEN r.totalPrice ELSE 0 END), 0),
        SUM(CASE WHEN r.createdAt >= :todayStart THEN 1 ELSE 0 END),
        COALESCE(SUM(CASE WHEN r.status = :confirmed AND r.createdAt >= :todayStart THEN r.totalPrice ELSE 0 END), 0)
      FROM Reservation r
      """)
  Object[] getDashboardStats(@Param("confirmed") ReservationStatus confirmed,
                             @Param("cancelled") ReservationStatus cancelled,
                             @Param("todayStart") LocalDateTime todayStart);
}