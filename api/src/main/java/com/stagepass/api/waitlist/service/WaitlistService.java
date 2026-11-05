package com.stagepass.api.waitlist.service;

import com.stagepass.api.waitlist.dto.WaitlistStatusResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.waitlist.WaitlistEntry;
import com.stagepass.domain.waitlist.WaitlistRepository;
import com.stagepass.domain.waitlist.WaitlistStatus;
import com.stagepass.infra.redis.WaitlistPopResult;
import com.stagepass.infra.redis.WaitlistRedisRepository;
import com.stagepass.kafka.event.WaitlistEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

  private final WaitlistRepository waitlistRepository;
  private final ShowRepository showRepository;
  private final UserRepository userRepository;
  private final WaitlistRedisRepository waitlistRedisRepository;
  private final EventPublisher eventPublisher;

  // 취소 대기 등록
  @Transactional
  public WaitlistStatusResponse join(Long showId, Long userId) {
    if (waitlistRedisRepository.isWaiting(showId, userId)) {
      throw new BusinessException(ErrorCode.WAITLIST_ALREADY_JOINED);
    }

    Show show = showRepository.findById(showId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOW_NOT_FOUND));
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    waitlistRedisRepository.add(showId, userId);
    waitlistRepository.save(WaitlistEntry.builder().show(show).user(user).build());

    Long rank = waitlistRedisRepository.getRank(showId, userId);
    Long total = waitlistRedisRepository.getSize(showId);

    log.info("[Waitlist] joined showId={} userId={} rank={}", showId, userId, rank);
    return new WaitlistStatusResponse(rank, total, "WAITING");
  }

  // 대기 순번 조회
  @Transactional(readOnly = true)
  public WaitlistStatusResponse getStatus(Long showId, Long userId) {
    Long rank = waitlistRedisRepository.getRank(showId, userId);
    Long total = waitlistRedisRepository.getSize(showId);

    if (rank == null) {
      return new WaitlistStatusResponse(null, total, "NOT_IN_WAITLIST");
    }

    WaitlistEntry entry = waitlistRepository.findByShowIdAndUserId(showId, userId).orElse(null);
    String status = (entry != null && entry.getStatus() == WaitlistStatus.NOTIFIED)
        ? "NOTIFIED" : "WAITING";

    return new WaitlistStatusResponse(rank, total, status);
  }

  // 취소 대기 이탈
  @Transactional
  public void leave(Long showId, Long userId) {
    waitlistRedisRepository.remove(showId, userId);
    waitlistRepository.findByShowIdAndUserId(showId, userId)
        .ifPresent(WaitlistEntry::cancel);
    log.info("[Waitlist] left showId={} userId={}", showId, userId);
  }

  // 예매 취소/만료/결제 실패 시 첫 번째 대기자에게 알림
  // Kafka 컨슈머에서만 호출한다(WaitlistReleaseConsumer, PaymentFailureConsumer).
  // 다른 @Transactional 안에서 직접 호출하면 안 된다 — 예전엔 REQUIRES_NEW 로 호출자와 분리했는데,
  // 호출자가 커넥션을 쥔 채 두 번째 커넥션을 요구해 동시 요청이 풀 크기에 닿으면 자기 교착에 빠지고,
  // 커넥션 획득/커밋 실패는 오히려 예매 취소를 롤백시켰다.
  @Transactional
  public void notifyNext(Long showId) {
    // 손상된 데이터 파싱 실패는 Repository에서 null 반환으로 처리
    WaitlistPopResult popped = waitlistRedisRepository.popFirstEntry(showId);
    if (popped == null) return;

    Long nextUserId = popped.userId();
    // 복구 시 원래 score(등록 시각)로 재삽입해야 순번 유지 — add()는 currentTimeMillis → 맨 뒤로 밀림
    double originalScore = popped.score();

    try {
      WaitlistEntry entry = waitlistRepository.findByShowIdAndUserId(showId, nextUserId)
          .orElseThrow(() -> new IllegalStateException(
              "Waitlist entry not found after Redis pop: showId=" + showId + " userId=" + nextUserId));
      entry.notify(LocalDateTime.now());
      eventPublisher.publishWaitlistNotified(new WaitlistEvent(showId, nextUserId));
      log.info("[Waitlist] notified showId={} userId={}", showId, nextUserId);
    } catch (Exception e) {
      // 원래 score로 복구 — add()는 currentTimeMillis를 score로 써서 순번이 소실됨
      waitlistRedisRepository.addWithScore(showId, nextUserId, originalScore);
      log.error("[Waitlist] notification failed, Redis restored showId={} userId={}", showId, nextUserId, e);
      // 다시 던져야 트랜잭션이 롤백되고 컨슈머가 ack 하지 않아 재시도한다 — 재시도 시 같은 대기자가 다시 pop 된다.
      // 삼키면 컨슈머가 성공으로 보고 ack 해서, 1순위는 Redis 에 복구되지만 다음 취소가 생길 때까지 알림이 안 간다
      throw e;
    }
  }
}
