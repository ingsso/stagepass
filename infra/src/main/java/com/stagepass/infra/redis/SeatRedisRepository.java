package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class SeatRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;

  private static final long HOLD_DURATION_SECONDS = 300L; // 5분
  private static final String SEAT_KEY_PREFIX = "seat:hold:";

  // 좌석 선점 시도 — SET NX PX (원자적)
  public boolean hold(Long seatId, Long userId) {
    String key = SEAT_KEY_PREFIX + seatId;
    String value = String.valueOf(userId);
    Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, value, Duration.ofSeconds(HOLD_DURATION_SECONDS));
    return Boolean.TRUE.equals(success);
  }

  // 선점 해제
  public void release(Long seatId, Long userId) {
    String key = SEAT_KEY_PREFIX + seatId;
    String currentHolder = redisTemplate.opsForValue().get(key);
    // 본인이 선점한 경우에만 해제
    if (String.valueOf(userId).equals(currentHolder)) {
      redisTemplate.delete(key);
    }
  }

  // 선점자 조회
  public String getHolder(Long seatId) {
    return redisTemplate.opsForValue().get(SEAT_KEY_PREFIX + seatId);
  }

  // 선점 여부 확인
  public boolean isHeld(Long seatId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(SEAT_KEY_PREFIX + seatId));
  }

  // 남은 TTL 조회 (초)
  public long getRemainingTtl(Long seatId) {
    Long ttl = redisTemplate.getExpire(SEAT_KEY_PREFIX + seatId, TimeUnit.SECONDS);
    return ttl != null ? ttl : 0L;
  }
}