package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
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

  // 첫 번째 대기자 꺼내기 (알림 후 제거)
  public Long popFirst(Long showId) {
    var result = redisTemplate.opsForZSet().popMin(KEY_PREFIX + showId);
    if (result == null) return null;
    try {
      return Long.parseLong(result.getValue());
    } catch (NumberFormatException e) {
      log.error("[Waitlist] popMin에서 잘못된 userId 형식 검출 showId={} value={}",
          showId, result.getValue(), e);
      throw new IllegalStateException(
          "Corrupted waitlist entry: showId=" + showId + " value=" + result.getValue(), e);
    }
  }

  // 대기열에서 제거
  public void remove(Long showId, Long userId) {
    redisTemplate.opsForZSet().remove(KEY_PREFIX + showId, String.valueOf(userId));
  }
}
