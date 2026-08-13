package com.stagepass.domain.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

  Optional<WaitlistEntry> findByShowIdAndUserId(Long showId, Long userId);

  boolean existsByShowIdAndUserIdAndStatus(Long showId, Long userId, WaitlistStatus status);

  List<WaitlistEntry> findByShowIdAndStatusOrderByCreatedAtAsc(Long showId, WaitlistStatus status);

  // 알림 후 10분이 지난 NOTIFIED 항목 (만료 처리용)
  List<WaitlistEntry> findByStatusAndNotifyExpiresAtBefore(WaitlistStatus status, LocalDateTime now);
}
