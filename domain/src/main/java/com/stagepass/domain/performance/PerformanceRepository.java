package com.stagepass.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

  List<Performance> findByGenre(String genre);

  @Query("SELECT p FROM Performance p WHERE p.title LIKE %:keyword%")
  List<Performance> searchByTitle(@Param("keyword") String keyword);
}