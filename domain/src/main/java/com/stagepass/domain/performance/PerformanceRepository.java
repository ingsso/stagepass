package com.stagepass.domain.performance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

  Page<Performance> findAll(Pageable pageable);

  @Query("SELECT p FROM Performance p WHERE p.title LIKE %:keyword%")
  Page<Performance> searchByTitle(@Param("keyword") String keyword, Pageable pageable);
}