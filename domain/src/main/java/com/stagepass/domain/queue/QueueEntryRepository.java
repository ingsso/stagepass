package com.stagepass.domain.queue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
  Optional<QueueEntry> findByShowIdAndUserId(Long showId, Long userId);
  int countByShowIdAndStatus(Long showId, QueueStatus status);
}