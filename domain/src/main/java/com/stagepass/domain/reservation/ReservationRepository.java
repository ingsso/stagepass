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
}