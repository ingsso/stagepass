package com.stagepass.domain.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
  List<ReservationSeat> findByReservationId(Long reservationId);

  @Query("SELECT rs FROM ReservationSeat rs JOIN FETCH rs.seat WHERE rs.reservation.id = :reservationId")
  List<ReservationSeat> findByReservationIdWithSeat(@Param("reservationId") Long reservationId);
}