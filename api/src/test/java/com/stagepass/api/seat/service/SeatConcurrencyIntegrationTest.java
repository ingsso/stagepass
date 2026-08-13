package com.stagepass.api.seat.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis(Testcontainers)를 사용한 좌석 동시 선점 통합 테스트.
 * SeatRedisRepository.hold()의 SET NX 로직을 직접 재현하여 원자성을 검증한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class SeatConcurrencyIntegrationTest {

    private static final String KEY_PREFIX = "seat:hold:";
    private static final long HOLD_TTL_MS = 300_000L;

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private RedisClient redisClient;

    @BeforeEach
    void setUp() {
        redisClient = RedisClient.create(
                RedisURI.builder()
                        .withHost(redis.getHost())
                        .withPort(redis.getMappedPort(6379))
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        redisClient.shutdown();
    }

    // SeatRedisRepository.hold() 재현 — SET NX PX 300000
    private boolean hold(Long seatId, Long userId) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisCommands<String, String> cmd = conn.sync();
            String result = cmd.set(
                    KEY_PREFIX + seatId,
                    String.valueOf(userId),
                    SetArgs.Builder.nx().px(HOLD_TTL_MS)
            );
            return "OK".equals(result);
        }
    }

    // SeatRedisRepository.release() 재현 — Lua 스크립트로 GET+DEL 원자적 실행
    private void release(Long seatId, Long userId) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisCommands<String, String> cmd = conn.sync();
            String script = "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
            cmd.eval(script, io.lettuce.core.ScriptOutputType.INTEGER,
                    new String[]{KEY_PREFIX + seatId}, String.valueOf(userId));
        }
    }

    // 테스트 보조: Redis 키에 직접 값 설정 (race condition 시뮬레이션용)
    private void forceSet(Long seatId, Long userId) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            conn.sync().set(KEY_PREFIX + seatId, String.valueOf(userId));
        }
    }

    private String getHolder(Long seatId) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            return conn.sync().get(KEY_PREFIX + seatId);
        }
    }

    private boolean isHeld(Long seatId) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            return conn.sync().exists(KEY_PREFIX + seatId) > 0;
        }
    }

    @Test
    @DisplayName("동시 선점 — 100명이 같은 좌석에 동시 요청 시 정확히 1명만 성공한다")
    void holdSeat_concurrent_exactlyOneSuccess() throws InterruptedException {
        Long seatId = 1L;
        int userCount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        for (long userId = 1; userId <= userCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (hold(seatId, uid)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get())
                .as("Redis SET NX 원자성 보장: 정확히 1명만 선점 성공해야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("선점 해제 후 다른 유저가 재선점 가능하다")
    void holdSeat_afterRelease_anotherUserCanHold() {
        Long seatId = 2L;

        boolean firstHold = hold(seatId, 1L);
        boolean duplicateHold = hold(seatId, 2L);

        release(seatId, 1L);

        boolean afterRelease = hold(seatId, 2L);

        assertThat(firstHold).isTrue();
        assertThat(duplicateHold).isFalse();
        assertThat(afterRelease).isTrue();
    }

    @Test
    @DisplayName("본인이 아닌 다른 유저는 선점을 해제할 수 없다")
    void releaseSeat_byOtherUser_doesNothing() {
        Long seatId = 3L;

        hold(seatId, 1L);
        release(seatId, 2L);

        assertThat(isHeld(seatId)).isTrue();
    }

    @Test
    @DisplayName("Lua 원자성 — user1이 해제하는 사이 user2가 선점해도 user2의 락이 보존된다")
    void releaseSeat_atomicLua_preservesNewHolderLock() {
        Long seatId = 4L;

        // user1 선점
        hold(seatId, 1L);

        // race condition 시뮬레이션: user1 해제 직전 user2가 강제로 선점
        // (실제 환경에서는 user1의 TTL 만료 후 user2가 hold하는 상황)
        forceSet(seatId, 2L);

        // user1이 자신의 락을 해제 시도 → Lua: 현재 holder가 user2이므로 삭제하지 않음
        release(seatId, 1L);

        // user2의 락은 그대로 보존되어야 한다
        assertThat(getHolder(seatId))
                .as("Lua 원자적 삭제: 다른 유저의 락을 침범하지 않아야 한다")
                .isEqualTo("2");
    }
}
