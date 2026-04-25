package com.stagepass.api.exchange.service;

import com.stagepass.domain.exchange.SeatExchange;
import com.stagepass.domain.exchange.SeatExchangeRepository;
import com.stagepass.domain.exchange.SeatExchangeStatus;
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
public class SeatExchangeExpiryScheduler {

  private final SeatExchangeRepository exchangeRepository;

  // 5분마다 24시간이 지난 PENDING 교환 제안을 EXPIRED 처리
  @Scheduled(fixedDelay = 300_000)
  @Transactional
  public void expirePendingExchanges() {
    List<SeatExchange> expired =
        exchangeRepository.findByStatusAndExpiresAtBefore(
            SeatExchangeStatus.PENDING, LocalDateTime.now());

    if (expired.isEmpty()) return;

    expired.forEach(SeatExchange::expire);
    log.info("[Scheduler] 교환 제안 만료 처리 count={}", expired.size());
  }
}
