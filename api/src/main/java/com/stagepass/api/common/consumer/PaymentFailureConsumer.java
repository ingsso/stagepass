package com.stagepass.api.common.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.api.waitlist.service.WaitlistService;
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

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class PaymentFailureConsumer {

  private final ReservationRepository reservationRepository;
  private final WaitlistService waitlistService;
  private final ObjectMapper objectMapper;

  // 결제 실패 → 취소 대기열 알림 (reservation-group과 별개 그룹으로 독립 소비)
  @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "waitlist-group")
  public void handlePaymentFailed(String message, Acknowledgment ack) {
    try {
      PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);
      log.info("[Waitlist] 결제 실패 감지 reservationId={}", event.getReservationId());

      reservationRepository.findById(event.getReservationId())
          .map(Reservation::getShow)
          .ifPresent(show -> waitlistService.notifyNext(show.getId()));

      ack.acknowledge();
    } catch (JsonProcessingException e) {
      log.error("[Waitlist] 역직렬화 실패 (포이즌 필) message={}", message, e);
      ack.acknowledge(); // 포이즌 필 — 재처리 없이 offset 커밋
    } catch (Exception e) {
      log.error("[Waitlist] 결제 실패 대기열 알림 처리 실패 message={}", message, e);
      throw new RuntimeException(e); // DefaultErrorHandler 재시도 위임
    }
  }
}
