package com.stagepass.notification.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RedisNotificationConfig {

  private final RedisConnectionFactory connectionFactory;

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisNotificationSubscriber subscriber) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    // notification:* 패턴 — 모든 유저 채널 구독 (인스턴스별 로컬 SSE로 전달)
    container.addMessageListener(subscriber, new PatternTopic(RedisNotificationPublisher.CHANNEL_PREFIX + "*"));
    return container;
  }
}
