package com.stagepass.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

  List<Seat> findByZoneId(Long zoneId);

  @Query("SELECT s FROM Seat s JOIN FETCH s.zone z WHERE z.show.id = :showId")
  List<Seat> findByShowIdWithZone(@Param("showId") Long showId);

  Optional<Seat> findByZoneIdAndSeatCode(Long zoneId, String seatCode);

  @Query("SELECT s FROM Seat s WHERE s.zone.show.id = :showId AND s.status = :status")
  List<Seat> findByShowIdAndStatus(@Param("showId") Long showId,
                                   @Param("status") SeatStatus status);

  @Query("SELECT COUNT(s) FROM Seat s WHERE s.zone.show.id = :showId AND s.status = 'AVAILABLE'")
  int countAvailableByShowId(@Param("showId") Long showId);

  @Query("SELECT s FROM Seat s JOIN FETCH s.zone WHERE s.id IN :ids")
  List<Seat> findAllByIdWithZone(@Param("ids") List<Long> ids);
}