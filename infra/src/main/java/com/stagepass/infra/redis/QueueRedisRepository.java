// infra/src/main/java/com/stagepass/infra/redis/QueueRedisRepository.java
package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;

  private static final String QUEUE_KEY_PREFIX = "queue:";

  // 대기열 진입 (score = 진입 시각 millis)
  public void enter(Long showId, Long userId) {
    String key = QUEUE_KEY_PREFIX + showId;
    double score = System.currentTimeMillis();
    redisTemplate.opsForZSet().add(key, String.valueOf(userId), score);
  }

  // 현재 순번 조회 (0-based → +1)
  public Long getRank(Long showId, Long userId) {
    String key = QUEUE_KEY_PREFIX + showId;
    Long rank = redisTemplate.opsForZSet().rank(key, String.valueOf(userId));
    return rank != null ? rank + 1 : null;
  }

  // 대기열 전체 인원
  public Long getSize(Long showId) {
    return redisTemplate.opsForZSet().size(QUEUE_KEY_PREFIX + showId);
  }

  // 앞에서 N명 조회 (입장 허가용)
  public Set<String> getTop(Long showId, int count) {
    return redisTemplate.opsForZSet().range(QUEUE_KEY_PREFIX + showId, 0, count - 1L);
  }

  // 대기열에서 제거
  public void remove(Long showId, Long userId) {
    redisTemplate.opsForZSet().remove(QUEUE_KEY_PREFIX + showId, String.valueOf(userId));
  }
}