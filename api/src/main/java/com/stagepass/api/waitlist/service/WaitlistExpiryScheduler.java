package com.stagepass.api.waitlist.service;

import com.stagepass.domain.waitlist.WaitlistEntry;
import com.stagepass.domain.waitlist.WaitlistRepository;
import com.stagepass.domain.waitlist.WaitlistStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistExpiryScheduler {

  private final WaitlistRepository waitlistRepository;

  // 1분마다 알림 후 10분이 지난 NOTIFIED 항목을 EXPIRED 처리
  @Scheduled(fixedDelay = 60_000)
  @Transactional
  public void expireNotifiedEntries() {
    List<WaitlistEntry> expired =
        waitlistRepository.findByStatusAndNotifyExpiresAtBefore(
            WaitlistStatus.NOTIFIED, LocalDateTime.now());

    if (expired.isEmpty()) return;

    expired.forEach(WaitlistEntry::expire);
    log.info("[Scheduler] 취소 대기 만료 처리 count={}", expired.size());
  }
}
