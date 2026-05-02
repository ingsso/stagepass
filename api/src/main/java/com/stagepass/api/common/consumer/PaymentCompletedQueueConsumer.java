package com.stagepass.api.common.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.api.queue.service.QueueService;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.reservation.ReservationRepository;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class PaymentCompletedQueueConsumer {

  private final ReservationRepository reservationRepository;
  private final QueueEntryRepository queueEntryRepository;
  private final QueueService queueService;
  private final ObjectMapper objectMapper;

  // 결제 완료 → 해당 유저의 큐 슬롯 소비 후 다음 배치 활성화
  @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "queue-payment-group")
  public void handlePaymentCompleted(String message, Acknowledgment ack) {
    try {
      PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);

      reservationRepository.findById(event.getReservationId()).ifPresentOrElse(
          reservation -> {
            Long showId = reservation.getShow().getId();
            Long userId = event.getUserId();

            Optional<QueueEntry> entry = queueEntryRepository.findByShowIdAndUserId(showId, userId);
            if (entry.isPresent() && entry.get().getStatus() == QueueStatus.ACTIVATED) {
              queueService.activateNextBatch(showId);
              log.info("[Queue] 결제 완료 후 다음 배치 활성화 showId={} userId={}", showId, userId);
            }
          },
          () -> log.warn("[Queue] 결제 완료 이벤트 수신했으나 예매 없음 — 이상 징후 reservationId={}", event.getReservationId())
      );

      ack.acknowledge();
    } catch (JsonProcessingException e) {
      log.error("[Queue] 역직렬화 실패 (포이즌 필) message={}", message, e);
      ack.acknowledge(); // 포이즌 필 — 재처리 없이 offset 커밋
    } catch (Exception e) {
      log.error("[Queue] 결제 완료 큐 처리 실패 message={}", message, e);
      throw new RuntimeException(e);
    }
  }
}
