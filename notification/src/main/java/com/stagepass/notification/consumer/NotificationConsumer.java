package com.stagepass.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.NotificationEvent;
import com.stagepass.kafka.event.QueueEvent;
import com.stagepass.kafka.event.SeatExchangeEvent;
import com.stagepass.kafka.event.TransferEvent;
import com.stagepass.kafka.event.WaitlistEvent;
import com.stagepass.notification.redis.RedisNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

  private final RedisNotificationPublisher publisher;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = KafkaTopics.NOTIFICATION_SEND, groupId = "notification-group")
  public void handleNotification(String message, Acknowledgment ack) {
    try {
      NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);
      log.info("[Notification] 알림 수신 userId={} type={}", event.getUserId(), event.getType());
      publisher.publish(event.getUserId(), event.getType(), event.getMessage());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 알림 처리 실패 message={}", message, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(topics = KafkaTopics.TRANSFER_CLAIMED, groupId = "notification-group")
  public void handleTransferClaimed(String message, Acknowledgment ack) {
    try {
      TransferEvent event = objectMapper.readValue(message, TransferEvent.class);
      log.info("[Notification] 양도 완료 fromUserId={}", event.getFromUserId());
      publisher.publish(event.getFromUserId(), "TRANSFER_CLAIMED", "회원님의 티켓이 양도되었습니다.");
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 양도 알림 처리 실패 message={}", message, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(topics = KafkaTopics.WAITLIST_NOTIFIED, groupId = "notification-group")
  public void handleWaitlistNotified(String message, Acknowledgment ack) {
    try {
      WaitlistEvent event = objectMapper.readValue(message, WaitlistEvent.class);
      log.info("[Notification] 취소 대기 알림 userId={}", event.getUserId());
      publisher.publish(event.getUserId(), "WAITLIST_NOTIFIED",
          "취소된 좌석이 생겼습니다! 10분 내로 예매를 완료해주세요.");
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 취소 대기 알림 처리 실패 message={}", message, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(topics = KafkaTopics.EXCHANGE_COMPLETED, groupId = "notification-group")
  public void handleExchangeCompleted(String message, Acknowledgment ack) {
    try {
      SeatExchangeEvent event = objectMapper.readValue(message, SeatExchangeEvent.class);
      log.info("[Notification] 자리 교환 완료 proposer={} receiver={}",
          event.getProposerId(), event.getReceiverId());
      publisher.publish(event.getProposerId(), "EXCHANGE_COMPLETED", "자리 교환이 완료되었습니다.");
      publisher.publish(event.getReceiverId(), "EXCHANGE_COMPLETED", "자리 교환이 완료되었습니다.");
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 교환 완료 알림 처리 실패 message={}", message, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(topics = KafkaTopics.QUEUE_ACTIVATED, groupId = "notification-group")
  public void handleQueueActivated(String message, Acknowledgment ack) {
    try {
      QueueEvent event = objectMapper.readValue(message, QueueEvent.class);
      log.info("[Notification] 대기열 입장 허가 userId={}", event.getUserId());
      publisher.publish(event.getUserId(), "QUEUE_ACTIVATED",
          "입장이 허가되었습니다. 지금 바로 좌석을 선택해주세요.");
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Notification] 대기열 알림 처리 실패 message={}", message, e);
      ack.acknowledge();
    }
  }
}
