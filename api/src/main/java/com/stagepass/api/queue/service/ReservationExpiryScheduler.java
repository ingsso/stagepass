package com.stagepass.api.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

  private final ReservationExpiryBatchProcessor batchProcessor;

  // 1분마다 만료된 PENDING 예매를 100건씩 배치 처리 — 각 배치는 별도 트랜잭션 (BachProcessor)
  @Scheduled(fixedDelay = 60_000)
  public void expireReservations() {
    List<?> batch;
    int totalProcessed = 0;

    do {
      batch = batchProcessor.processNextBatch();
      totalProcessed += batch.size();
    } while (batch.size() == ReservationExpiryBatchProcessor.BATCH_SIZE);

    if (totalProcessed > 0) {
      log.info("[Scheduler] 예매 만료 처리 완료 total={}", totalProcessed);
    }
  }
}