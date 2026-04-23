package com.stagepass.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.NotificationEvent;
import com.stagepass.kafka.event.QueueEvent;
import com.stagepass.kafka.event.TransferEvent;
import com.stagepass.notification.service.SseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

  private final SseNotificationService sseNotificationService;
  private final ObjectMapper objectMapper;

  // 일반 알림 (예매 확정, 결제 실패 등)
  @KafkaListener(topics = KafkaTopics.NOTIFICATION_SEND, groupId = "notification-group")
  public void handleNotification(String message, Acknowledgment ack) {
    try {
      NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
      log.info("[Notification] 알림 수신 userId={} type={}", event.getUserId(), event.getType());
      sseNotificationService.sendToUser(event.getUserId(), event.getType(), event.getMessage());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 알림 처리 실패 message={}", message, e);
    }
  }

  // 양도 완료 알림 (양도자에게)
  @KafkaListener(topics = KafkaTopics.TRANSFER_CLAIMED, groupId = "notification-group")
  public void handleTransferClaimed(String message, Acknowledgment ack) {
    try {
      TransferEvent event = objectMapper.readValue(message, TransferEvent.class);
      log.info("[Notification] 양도 완료 fromUserId={}", event.getFromUserId());
      sseNotificationService.sendToUser(
          event.getFromUserId(),
          "TRANSFER_CLAIMED",
          "회원님의 티켓이 양도되었습니다."
      );
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 양도 알림 처리 실패 message={}", message, e);
    }
  }

  // 대기열 입장 허가 알림
  @KafkaListener(topics = KafkaTopics.QUEUE_ACTIVATED, groupId = "notification-group")
  public void handleQueueActivated(String message, Acknowledgment ack) {
    try {
      QueueEvent event = objectMapper.readValue(message, QueueEvent.class);
      log.info("[Notification] 대기열 입장 허가 userId={}", event.getUserId());
      sseNotificationService.sendToUser(
          event.getUserId(),
          "QUEUE_ACTIVATED",
          "입장이 허가되었습니다. 지금 바로 좌석을 선택해주세요."
      );
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 대기열 알림 처리 실패 message={}", message, e);
    }
  }
}