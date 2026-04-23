package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TransferRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;

  private static final String KEY_PREFIX = "transfer:claim:";
  private static final long LOCK_TTL_SECONDS = 30L;

  // 양도 수락 선점 — SET NX (동시 수락 방지)
  public boolean claim(Long transferId, Long userId) {
    String key = KEY_PREFIX + transferId;
    Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, String.valueOf(userId), Duration.ofSeconds(LOCK_TTL_SECONDS));
    return Boolean.TRUE.equals(success);
  }

  public void release(Long transferId) {
    redisTemplate.delete(KEY_PREFIX + transferId);
  }
}
