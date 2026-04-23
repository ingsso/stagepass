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
@Testcontainers
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

    // SeatRedisRepository.release() 재현 — 본인 소유인 경우만 삭제
    private void release(Long seatId, Long userId) {
        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisCommands<String, String> cmd = conn.sync();
            String current = cmd.get(KEY_PREFIX + seatId);
            if (String.valueOf(userId).equals(current)) {
                cmd.del(KEY_PREFIX + seatId);
            }
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
}
