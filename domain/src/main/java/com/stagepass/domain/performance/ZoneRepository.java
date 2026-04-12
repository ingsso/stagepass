package com.stagepass.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
  List<Zone> findByShowId(Long showId);
}