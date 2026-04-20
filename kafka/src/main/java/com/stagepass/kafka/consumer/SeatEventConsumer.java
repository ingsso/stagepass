package com.stagepass.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.domain.performance.Seat;
import com.stagepass.domain.performance.SeatRepository;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.SeatHoldEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class SeatEventConsumer {

  private final SeatRepository seatRepository;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = KafkaTopics.SEAT_HOLD_EXPIRED, groupId = "seat-group")
  public void handleSeatHoldExpired(String message, Acknowledgment ack) {
    try {
      SeatHoldEvent event = objectMapper.readValue(message, SeatHoldEvent.class);
      log.info("[Kafka] 좌석 선점 만료 seatId={} userId={}", event.getSeatId(), event.getUserId());
      // DB 상태는 AVAILABLE 유지 (Redis TTL 만료로 자동 해제)
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] 좌석 선점 만료 처리 실패 message={}", message, e);
    }
  }

  @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "seat-group")
  public void handlePaymentFailed(String message, Acknowledgment ack) {
    try {
      com.stagepass.kafka.event.PaymentResultEvent event =
          objectMapper.readValue(message, com.stagepass.kafka.event.PaymentResultEvent.class);
      log.info("[Kafka] 결제 실패 → 좌석 해제 reservationId={}", event.getReservationId());
      // 해당 예매의 좌석들 AVAILABLE 복원은 ReservationConsumer에서 처리
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] 결제 실패 좌석 해제 처리 실패 message={}", message, e);
    }
  }

  @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "seat-group")
  public void handlePaymentCompleted(String message, Acknowledgment ack) {
    try {
      com.stagepass.kafka.event.PaymentResultEvent event =
          objectMapper.readValue(message, com.stagepass.kafka.event.PaymentResultEvent.class);
      log.info("[Kafka] 결제 완료 → 좌석 확정 reservationId={}", event.getReservationId());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] 결제 완료 좌석 확정 처리 실패 message={}", message, e);
    }
  }
}