package com.stagepass.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.QueueEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class QueueEventConsumer {

  private final QueueEntryRepository queueEntryRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  @KafkaListener(topics = KafkaTopics.QUEUE_ACTIVATED, groupId = "queue-group")
  public void handleQueueActivated(String message, Acknowledgment ack) {
    try {
      QueueEvent event = objectMapper.readValue(message, QueueEvent.class);
      queueEntryRepository.findByShowIdAndUserId(event.getShowId(), event.getUserId())
          .ifPresent(QueueEntry::activate);
      log.info("[Kafka] queue activated showId={} userId={}", event.getShowId(), event.getUserId());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] queue activation processing failed message={}", message, e);
      ack.acknowledge(); // 부가 기능 — 손실 허용하고 offset 커밋
    }
  }
}