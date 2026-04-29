package com.stagepass.domain.queue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {
  Optional<QueueEntry> findByShowIdAndUserId(Long showId, Long userId);
  int countByShowIdAndStatus(Long showId, QueueStatus status);

  @Query("SELECT e FROM QueueEntry e WHERE e.show.id = :showId AND e.user.id IN :userIds")
  List<QueueEntry> findByShowIdAndUserIdIn(@Param("showId") Long showId,
                                           @Param("userIds") List<Long> userIds);
}