package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;

  private static final String REFRESH_KEY_PREFIX = "refresh:";
  private static final long REFRESH_TTL_DAYS = 7L;

  public void save(Long userId, String refreshToken) {
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + userId,
        refreshToken,
        Duration.ofDays(REFRESH_TTL_DAYS)
    );
  }

  public String get(Long userId) {
    return redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
  }

  public void delete(Long userId) {
    redisTemplate.delete(REFRESH_KEY_PREFIX + userId);
  }

  public boolean exists(Long userId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_KEY_PREFIX + userId));
  }
}