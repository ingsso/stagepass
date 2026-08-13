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
  private static final String QUEUE_SEQ_KEY_PREFIX = "queue:seq:";

  /**
   * 대기열 진입. score = 회차별 INCR 시퀀스.
   *
   * score 로 System.currentTimeMillis() 를 쓰면 안 된다. 밀리초 단위라 동시 진입 시
   * 동점이 대량으로 발생하고, 동점 멤버는 Redis 가 사전순으로 정렬한다. ZADD 와 ZRANK 는
   * 별도 왕복이므로, 내가 ZADD 한 뒤 ZRANK 를 읽기 전에 같은 score 의 더 작은 userId 가
   * 끼어들면 내 순번이 밀린다 → 두 사용자가 같은 순번을 읽는다.
   * (1,000 VU 부하에서 중복 순번 10건 / 누락 10건 실측)
   *
   * INCR 시퀀스는 고유하고 단조 증가하므로 뒤에 들어온 멤버가 앞사람 순번을 밀 수 없다.
   * ZADD NX(addIfAbsent) 로 이미 있는 멤버의 score 는 갱신하지 않는다 —
   * 재진입(멱등 호출)이 사용자를 대기열 뒤로 밀어내지 않도록 한다.
   */
  public void enter(Long showId, Long userId) {
    String key = QUEUE_KEY_PREFIX + showId;
    Long seq = redisTemplate.opsForValue().increment(QUEUE_SEQ_KEY_PREFIX + showId);
    if (seq == null) {
      throw new IllegalStateException("대기열 시퀀스 발급 실패 showId=" + showId);
    }
    redisTemplate.opsForZSet().addIfAbsent(key, String.valueOf(userId), seq);
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