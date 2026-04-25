package com.stagepass.api.transfer.service;

import com.stagepass.domain.transfer.Transfer;
import com.stagepass.domain.transfer.TransferRepository;
import com.stagepass.domain.transfer.TransferStatus;
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
public class TransferExpiryScheduler {

  private final TransferRepository transferRepository;

  // 5분마다 공연 시작 시각이 지난 OPEN 양도 글 자동 만료
  @Scheduled(fixedDelay = 300_000)
  @Transactional
  public void expireTransfers() {
    List<Transfer> expired =
        transferRepository.findByStatusAndExpiresAtBefore(TransferStatus.OPEN, LocalDateTime.now());

    if (expired.isEmpty()) return;

    expired.forEach(Transfer::expire);
    log.info("[Scheduler] 양도 글 만료 처리 count={}", expired.size());
  }
}
