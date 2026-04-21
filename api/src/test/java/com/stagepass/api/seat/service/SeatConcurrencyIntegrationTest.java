package com.stagepass.api.seat.service;

import com.stagepass.infra.redis.SeatRedisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
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
 * Redis SET NX의 원자성으로 경쟁 조건 없이 정확히 1명만 성공해야 한다.
 */
@Testcontainers
class SeatConcurrencyIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private SeatRedisRepository seatRedisRepository;
    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();

        seatRedisRepository = new SeatRedisRepository(template);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
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
                    start.await(); // 모든 스레드가 준비된 뒤 동시에 출발
                    if (seatRedisRepository.hold(seatId, uid)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(); // 100개 스레드 모두 준비 완료 대기
        start.countDown(); // 동시 출발

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

        boolean firstHold = seatRedisRepository.hold(seatId, 1L);
        boolean duplicateHold = seatRedisRepository.hold(seatId, 2L); // 1번 유저가 보유 중

        seatRedisRepository.release(seatId, 1L); // 1번 유저 해제

        boolean afterRelease = seatRedisRepository.hold(seatId, 2L); // 2번 유저 재시도

        assertThat(firstHold).isTrue();
        assertThat(duplicateHold).isFalse();
        assertThat(afterRelease).isTrue();
    }

    @Test
    @DisplayName("본인이 아닌 다른 유저는 선점을 해제할 수 없다")
    void releaseSeat_byOtherUser_doesNothing() {
        Long seatId = 3L;

        seatRedisRepository.hold(seatId, 1L);
        seatRedisRepository.release(seatId, 2L); // 다른 유저가 해제 시도

        assertThat(seatRedisRepository.isHeld(seatId)).isTrue(); // 여전히 선점 중
    }
}
