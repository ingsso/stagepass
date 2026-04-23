package com.stagepass.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class EventPublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public void publishSeatHold(SeatHoldEvent event) {
    publish(KafkaTopics.SEAT_HOLD, String.valueOf(event.getSeatId()), event);
  }

  public void publishSeatHoldExpired(SeatHoldEvent event) {
    publish(KafkaTopics.SEAT_HOLD_EXPIRED, String.valueOf(event.getSeatId()), event);
  }

  public void publishSeatReleased(SeatHoldEvent event) {
    publish(KafkaTopics.SEAT_RELEASED, String.valueOf(event.getSeatId()), event);
  }

  public void publishPaymentRequested(PaymentRequestedEvent event) {
    publish(KafkaTopics.PAYMENT_REQUESTED, String.valueOf(event.getReservationId()), event);
  }

  public void publishPaymentCompleted(PaymentResultEvent event) {
    publish(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(event.getReservationId()), event);
  }

  public void publishPaymentFailed(PaymentResultEvent event) {
    publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(event.getReservationId()), event);
  }

  public void publishReservationConfirmed(ReservationEvent event) {
    publish(KafkaTopics.RESERVATION_CONFIRMED, String.valueOf(event.getReservationId()), event);
  }

  public void publishReservationCancelled(ReservationEvent event) {
    publish(KafkaTopics.RESERVATION_CANCELLED, String.valueOf(event.getReservationId()), event);
  }

  public void publishNotification(NotificationEvent event) {
    publish(KafkaTopics.NOTIFICATION_SEND, String.valueOf(event.getUserId()), event);
  }

  public void publishQueueEntered(QueueEvent event) {
    publish(KafkaTopics.QUEUE_ENTERED, String.valueOf(event.getUserId()), event);
  }

  public void publishQueueActivated(QueueEvent event) {
    publish(KafkaTopics.QUEUE_ACTIVATED, String.valueOf(event.getUserId()), event);
  }

  public void publishTransferClaimed(TransferEvent event) {
    publish(KafkaTopics.TRANSFER_CLAIMED, String.valueOf(event.getTransferId()), event);
  }

  public void publishWaitlistNotified(WaitlistEvent event) {
    publish(KafkaTopics.WAITLIST_NOTIFIED, String.valueOf(event.getUserId()), event);
  }

  public void publishExchangeCompleted(SeatExchangeEvent event) {
    publish(KafkaTopics.EXCHANGE_COMPLETED, String.valueOf(event.getExchangeId()), event);
  }

  private void publish(String topic, String key, Object event) {
    try {
      String message = objectMapper.writeValueAsString(event);
      kafkaTemplate.send(topic, key, message)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              log.error("[Kafka] 발행 실패 topic={} key={} error={}", topic, key, ex.getMessage());
            } else {
              log.debug("[Kafka] 발행 성공 topic={} key={} offset={}",
                  topic, key, result.getRecordMetadata().offset());
            }
          });
    } catch (JsonProcessingException e) {
      log.error("[Kafka] 직렬화 실패 topic={} error={}", topic, e.getMessage());
      throw new RuntimeException("Kafka 이벤트 직렬화 실패", e);
    }
  }
}