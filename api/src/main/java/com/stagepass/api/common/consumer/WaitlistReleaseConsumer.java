package com.stagepass.api.common.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.ReservationEvent;
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
public class WaitlistReleaseConsumer {

  private final WaitlistService waitlistService;
  private final ObjectMapper objectMapper;

  // 예매 취소/만료 → 취소 대기 1순위 알림
  // 취소·만료 트랜잭션 밖에서 notifyNext 를 실행해 요청당 DB 커넥션을 1개로 유지한다
  @KafkaListener(
      topics = {KafkaTopics.RESERVATION_CANCELLED, KafkaTopics.RESERVATION_EXPIRED},
      groupId = "waitlist-group")
  public void handleReservationReleased(String message, Acknowledgment ack) {
    try {
      ReservationEvent event = objectMapper.readValue(message, ReservationEvent.class);
      log.info("[Waitlist] reservation released reservationId={} showId={}",
          event.getReservationId(), event.getShowId());

      waitlistService.notifyNext(event.getShowId());

      ack.acknowledge();
    } catch (JsonProcessingException e) {
      log.error("[Waitlist] deserialization failed (poison pill) message={}", message, e);
      ack.acknowledge(); // 포이즌 필 — 재처리 없이 offset 커밋
    } catch (Exception e) {
      log.error("[Waitlist] reservation release waitlist notification failed message={}", message, e);
      throw new RuntimeException(e); // DefaultErrorHandler 재시도 위임
    }
  }
}
