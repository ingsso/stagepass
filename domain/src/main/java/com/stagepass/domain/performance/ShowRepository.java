package com.stagepass.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

  List<Show> findByPerformanceId(Long performanceId);

  @Query("SELECT s FROM Show s WHERE s.performance.id = :performanceId AND s.status = :status")
  List<Show> findByPerformanceIdAndStatus(@Param("performanceId") Long performanceId,
                                          @Param("status") ShowStatus status);
}