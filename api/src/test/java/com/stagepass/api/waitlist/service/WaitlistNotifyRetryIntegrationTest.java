package com.stagepass.api.waitlist.service;

import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.waitlist.WaitlistEntry;
import com.stagepass.domain.waitlist.WaitlistRepository;
import com.stagepass.domain.waitlist.WaitlistStatus;
import com.stagepass.infra.redis.WaitlistRedisRepository;
import com.stagepass.kafka.event.WaitlistEvent;
import com.stagepass.kafka.producer.EventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * notifyNext() 실패 → 컨슈머 재시도 시 같은 대기자가 다시 pop 되는지 검증.
 *
 * 실제 Redis(Testcontainers)의 ZPOPMIN / ZADD 로 순번 복구를 확인한다.
 * DB(WaitlistRepository)는 첫 시도에만 실패하도록 mock 한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class WaitlistNotifyRetryIntegrationTest {

  private static final Long SHOW_ID = 1L;
  private static final Long FIRST_USER = 10L;
  private static final Long SECOND_USER = 20L;

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private WaitlistRedisRepository waitlistRedisRepository;

  private final WaitlistRepository waitlistRepository = mock(WaitlistRepository.class);
  private final EventPublisher eventPublisher = mock(EventPublisher.class);
  private WaitlistService waitlistService;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    waitlistRedisRepository = new WaitlistRedisRepository(redisTemplate);
    waitlistService = new WaitlistService(
        waitlistRepository, mock(ShowRepository.class), mock(UserRepository.class),
        waitlistRedisRepository, eventPublisher);

    // 1순위 FIRST_USER, 2순위 SECOND_USER
    waitlistRedisRepository.addWithScore(SHOW_ID, FIRST_USER, 1_000.0);
    waitlistRedisRepository.addWithScore(SHOW_ID, SECOND_USER, 2_000.0);
  }

  @AfterEach
  void tearDown() {
    redisTemplate.delete("waitlist:" + SHOW_ID);
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("첫 시도 DB 실패 → Redis 원래 순번 복구 + 예외 → 재시도에서 같은 대기자가 NOTIFIED")
  void notifyNext_실패후_재시도_같은대기자_알림() {
    WaitlistEntry firstUserEntry = WaitlistEntry.builder().build();
    given(waitlistRepository.findByShowIdAndUserId(SHOW_ID, FIRST_USER))
        .willThrow(new RuntimeException("DB 장애"))
        .willReturn(Optional.of(firstUserEntry));

    // 1차 시도: 실패 → 예외가 컨슈머까지 전파되어야 ack 없이 재시도된다
    assertThatThrownBy(() -> waitlistService.notifyNext(SHOW_ID))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB 장애");

    // FIRST_USER 가 원래 score 로 복구되어 여전히 1순위
    assertThat(waitlistRedisRepository.getRank(SHOW_ID, FIRST_USER)).isEqualTo(1L);
    assertThat(waitlistRedisRepository.getRank(SHOW_ID, SECOND_USER)).isEqualTo(2L);
    then(eventPublisher).should(never()).publishWaitlistNotified(any());

    // 2차 시도(컨슈머 재시도): 같은 FIRST_USER 가 pop 되어 NOTIFIED
    waitlistService.notifyNext(SHOW_ID);

    ArgumentCaptor<WaitlistEvent> captor = ArgumentCaptor.forClass(WaitlistEvent.class);
    then(eventPublisher).should(times(1)).publishWaitlistNotified(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(FIRST_USER);
    assertThat(firstUserEntry.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);

    // FIRST_USER 는 대기열에서 빠지고 SECOND_USER 가 1순위
    assertThat(waitlistRedisRepository.isWaiting(SHOW_ID, FIRST_USER)).isFalse();
    assertThat(waitlistRedisRepository.getRank(SHOW_ID, SECOND_USER)).isEqualTo(1L);
  }
}
