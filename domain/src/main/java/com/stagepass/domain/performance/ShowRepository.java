package com.stagepass.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

  List<Show> findByPerformanceId(Long performanceId);

  // 회차별 확정 예매 수 집계 — N+1 방지 (어드민 대시보드용)
  @Query("""
      SELECT s, COUNT(r)
      FROM Show s
      LEFT JOIN Reservation r ON r.show.id = s.id AND r.status = 'CONFIRMED'
      WHERE s.performance.id = :performanceId
      GROUP BY s.id
      ORDER BY s.showDatetime ASC
      """)
  List<Object[]> findWithConfirmedReservationCount(@Param("performanceId") Long performanceId);

  @Query("SELECT s FROM Show s WHERE s.performance.id = :performanceId AND s.status = :status")
  List<Show> findByPerformanceIdAndStatus(@Param("performanceId") Long performanceId,
                                          @Param("status") ShowStatus status);
}