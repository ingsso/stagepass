package com.stagepass.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.infra.kafka.KafkaTopics;
import com.stagepass.kafka.event.QueueEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
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
      log.info("[Kafka] 대기열 입장 허가 showId={} userId={}", event.getShowId(), event.getUserId());
      ack.acknowledge();
    } catch (Exception e) {
      log.error("[Kafka] 대기열 입장 처리 실패 message={}", message, e);
    }
  }
}