package com.stagepass.domain.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

  Optional<WaitlistEntry> findByShowIdAndUserId(Long showId, Long userId);

  boolean existsByShowIdAndUserIdAndStatus(Long showId, Long userId, WaitlistStatus status);

  List<WaitlistEntry> findByShowIdAndStatusOrderByCreatedAtAsc(Long showId, WaitlistStatus status);
}
