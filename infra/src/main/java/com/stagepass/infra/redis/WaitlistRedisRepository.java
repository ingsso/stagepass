package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class WaitlistRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;
  private static final String KEY_PREFIX = "waitlist:";

  // 취소 대기 등록 (score = 등록 시각)
  public void add(Long showId, Long userId) {
    double score = System.currentTimeMillis();
    redisTemplate.opsForZSet().add(KEY_PREFIX + showId, String.valueOf(userId), score);
  }

  // 대기 순번 조회 (1-based)
  public Long getRank(Long showId, Long userId) {
    Long rank = redisTemplate.opsForZSet().rank(KEY_PREFIX + showId, String.valueOf(userId));
    return rank != null ? rank + 1 : null;
  }

  // 대기 인원 수
  public Long getSize(Long showId) {
    Long size = redisTemplate.opsForZSet().size(KEY_PREFIX + showId);
    return size != null ? size : 0L;
  }

  // 이미 대기 중인지
  public boolean isWaiting(Long showId, Long userId) {
    return redisTemplate.opsForZSet().rank(KEY_PREFIX + showId, String.valueOf(userId)) != null;
  }

  // 첫 번째 대기자 꺼내기 — userId와 원래 score(등록 시각) 함께 반환
  // score는 복구 시 원래 순번 유지에 사용 (api 모듈이 ZSetOperations에 직접 의존하지 않도록 DTO 래핑)
  public WaitlistPopResult popFirstEntry(Long showId) {
    var result = redisTemplate.opsForZSet().popMin(KEY_PREFIX + showId);
    if (result == null || result.getValue() == null) return null;
    try {
      Long userId = Long.parseLong(result.getValue());
      double score = result.getScore() != null ? result.getScore() : 0.0;
      return new WaitlistPopResult(userId, score);
    } catch (NumberFormatException e) {
      log.error("[Waitlist] invalid userId format from popMin showId={} value={}", showId, result.getValue(), e);
      return null; // 손상된 항목 — 스킵
    }
  }

  // 원래 score로 복구 삽입 — 예외 발생 시 순번을 잃지 않도록 보장
  public void addWithScore(Long showId, Long userId, double score) {
    redisTemplate.opsForZSet().add(KEY_PREFIX + showId, String.valueOf(userId), score);
  }

  // 대기열에서 제거
  public void remove(Long showId, Long userId) {
    redisTemplate.opsForZSet().remove(KEY_PREFIX + showId, String.valueOf(userId));
  }
}
