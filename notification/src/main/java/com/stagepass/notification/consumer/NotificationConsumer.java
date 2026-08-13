package com.stagepass.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

  private final RedisNotificationPublisher publisher;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = KafkaTopics.NOTIFICATION_SEND, groupId = "notification-group")
  public void handleNotification(String message, Acknowledgment ack) {
    handle(message, ack, NotificationEvent.class, event -> {
      log.info("[Notification] received userId={} type={}", event.getUserId(), event.getType());
      publisher.publish(event.getUserId(), event.getType(), event.getMessage());
    });
  }

  @KafkaListener(topics = KafkaTopics.TRANSFER_CLAIMED, groupId = "notification-group")
  public void handleTransferClaimed(String message, Acknowledgment ack) {
    handle(message, ack, TransferEvent.class, event -> {
      log.info("[Notification] transfer claimed fromUserId={}", event.getFromUserId());
      publisher.publish(event.getFromUserId(), "TRANSFER_CLAIMED", "Your ticket has been transferred.");
    });
  }

  @KafkaListener(topics = KafkaTopics.WAITLIST_NOTIFIED, groupId = "notification-group")
  public void handleWaitlistNotified(String message, Acknowledgment ack) {
    handle(message, ack, WaitlistEvent.class, event -> {
      log.info("[Notification] waitlist notified userId={}", event.getUserId());
      publisher.publish(event.getUserId(), "WAITLIST_NOTIFIED",
          "A cancelled seat is available! Please complete your reservation within 10 minutes.");
    });
  }

  @KafkaListener(topics = KafkaTopics.EXCHANGE_COMPLETED, groupId = "notification-group")
  public void handleExchangeCompleted(String message, Acknowledgment ack) {
    handle(message, ack, SeatExchangeEvent.class, event -> {
      log.info("[Notification] seat exchange completed proposer={} receiver={}",
          event.getProposerId(), event.getReceiverId());
      publisher.publish(event.getProposerId(), "EXCHANGE_COMPLETED", "Seat exchange has been completed.");
      publisher.publish(event.getReceiverId(), "EXCHANGE_COMPLETED", "Seat exchange has been completed.");
    });
  }

  @KafkaListener(topics = KafkaTopics.QUEUE_ACTIVATED, groupId = "notification-group")
  public void handleQueueActivated(String message, Acknowledgment ack) {
    handle(message, ack, QueueEvent.class, event -> {
      log.info("[Notification] queue activated userId={}", event.getUserId());
      publisher.publish(event.getUserId(), "QUEUE_ACTIVATED",
          "You have been admitted. Please select your seat now.");
    });
  }

  private <T> void handle(String message, Acknowledgment ack, Class<T> type, Consumer<T> processor) {
    try {
      T event = objectMapper.readValue(message, type);
      processor.accept(event);
      ack.acknowledge();
    } catch (JsonProcessingException e) {
      log.error("[Notification] deserialization failed (poison pill) message={}", message, e);
      ack.acknowledge(); // 포이즌 필 — 재처리 없이 offset 커밋
    } catch (Exception e) {
      log.error("[Notification] processing failed message={}", message, e);
      throw new RuntimeException(e); // DefaultErrorHandler 재시도 위임
    }
  }
}
