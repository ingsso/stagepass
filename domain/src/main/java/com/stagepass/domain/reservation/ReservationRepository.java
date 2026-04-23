package com.stagepass.domain.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  List<Reservation> findByUserId(Long userId);

  List<Reservation> findByShowId(Long showId);

  // 만료 처리 대상 조회 (스케줄러용)
  @Query("SELECT r FROM Reservation r WHERE r.status = 'PENDING' AND r.expiresAt < :now")
  List<Reservation> findExpiredReservations(@Param("now") LocalDateTime now);

  Optional<Reservation> findByIdAndUserId(Long id, Long userId);

  // 대시보드 집계 쿼리
  long countByStatus(ReservationStatus status);

  @Query("SELECT COALESCE(SUM(r.totalPrice), 0) FROM Reservation r WHERE r.status = :status")
  long sumTotalPriceByStatus(@Param("status") ReservationStatus status);

  @Query("SELECT COUNT(r) FROM Reservation r WHERE r.createdAt >= :from")
  long countByCreatedAtAfter(@Param("from") LocalDateTime from);

  @Query("SELECT COALESCE(SUM(r.totalPrice), 0) FROM Reservation r WHERE r.status = :status AND r.createdAt >= :from")
  long sumTotalPriceByStatusAndCreatedAtAfter(@Param("status") ReservationStatus status,
                                              @Param("from") LocalDateTime from);
}