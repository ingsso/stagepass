package com.stagepass.notification.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.notification.service.SseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisNotificationSubscriber implements MessageListener {

  private final SseNotificationService sseNotificationService;
  private final ObjectMapper objectMapper;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      NotificationMessage payload = objectMapper.readValue(message.getBody(), NotificationMessage.class);
      sseNotificationService.sendToUser(payload.getUserId(), payload.getType(), payload.getMessage());
    } catch (Exception e) {
      log.error("[Redis] 알림 수신 처리 실패 message={}", new String(message.getBody()), e);
    }
  }
}
