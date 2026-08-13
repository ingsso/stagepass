package com.stagepass.notification.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNotificationPublisher {

  private final RedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;

  static final String CHANNEL_PREFIX = "notification:";

  public void publish(Long userId, String type, String message) {
    try {
      String payload = objectMapper.writeValueAsString(new NotificationMessage(userId, type, message));
      redisTemplate.convertAndSend(CHANNEL_PREFIX + userId, payload);
    } catch (JsonProcessingException e) {
      log.error("[Redis] notification publish serialization failed userId={} type={}", userId, type, e);
    }
  }
}
