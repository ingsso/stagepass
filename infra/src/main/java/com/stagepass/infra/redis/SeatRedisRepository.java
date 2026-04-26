package com.stagepass.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class SeatRedisRepository {

  private final RedisTemplate<String, String> redisTemplate;

  private static final long HOLD_DURATION_SECONDS = 300L; // 5분
  private static final String SEAT_KEY_PREFIX = "seat:hold:";

  // 본인이 선점한 경우에만 원자적으로 삭제 (GET+DELETE 사이 race condition 방지)
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
      Long.class
  );

  // 좌석 선점 시도 — SET NX PX (원자적)
  public boolean hold(Long seatId, Long userId) {
    String key = SEAT_KEY_PREFIX + seatId;
    String value = String.valueOf(userId);
    Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(key, value, Duration.ofSeconds(HOLD_DURATION_SECONDS));
    return Boolean.TRUE.equals(success);
  }

  // 선점 해제 — Lua 스크립트로 GET+DEL 원자적 실행
  public void release(Long seatId, Long userId) {
    String key = SEAT_KEY_PREFIX + seatId;
    redisTemplate.execute(RELEASE_SCRIPT, List.of(key), String.valueOf(userId));
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

  // 좌석 목록 TTL 일괄 조회 — Pipeline으로 N번 왕복 → 1번 왕복
  public Map<Long, Long> getBulkRemainingTtl(List<Long> seatIds) {
    List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
      for (Long seatId : seatIds) {
        byte[] key = redisTemplate.getStringSerializer().serialize(SEAT_KEY_PREFIX + seatId);
        connection.keyCommands().ttl(key);
      }
      return null;
    });

    Map<Long, Long> ttlMap = new HashMap<>();
    for (int i = 0; i < seatIds.size(); i++) {
      Long ttl = (Long) results.get(i);
      ttlMap.put(seatIds.get(i), ttl != null ? ttl : 0L);
    }
    return ttlMap;
  }
}