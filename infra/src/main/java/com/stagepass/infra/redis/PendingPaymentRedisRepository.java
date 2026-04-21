package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class PendingPaymentRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;

  private static final String KEY_PREFIX = "payment:pending:";
  private static final long TTL_MINUTES = 10L; // 결제창 유효 시간

  // orderId → reservationId 저장
  public void save(String orderId, Long reservationId) {
    redisTemplate.opsForValue().set(
        KEY_PREFIX + orderId,
        String.valueOf(reservationId),
        Duration.ofMinutes(TTL_MINUTES)
    );
  }

  // orderId로 reservationId 조회
  public Long get(String orderId) {
    String value = redisTemplate.opsForValue().get(KEY_PREFIX + orderId);
    return value != null ? Long.parseLong(value) : null;
  }

  public void delete(String orderId) {
    redisTemplate.delete(KEY_PREFIX + orderId);
  }
}
