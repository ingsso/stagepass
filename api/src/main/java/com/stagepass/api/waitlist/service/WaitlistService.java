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
import org.springframework.transaction.annotation.Propagation;
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

  // 예매 취소/만료 발생 시 첫 번째 대기자에게 알림
  // REQUIRES_NEW: 호출자 트랜잭션과 분리 → 알림 실패가 예매 취소를 롤백시키지 않음
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    }
  }
}
