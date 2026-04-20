package com.stagepass.infra.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

  @Bean public NewTopic seatHold()           { return TopicBuilder.name(KafkaTopics.SEAT_HOLD).partitions(3).replicas(1).build(); }
  @Bean public NewTopic seatHoldExpired()    { return TopicBuilder.name(KafkaTopics.SEAT_HOLD_EXPIRED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic seatReleased()       { return TopicBuilder.name(KafkaTopics.SEAT_RELEASED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic paymentRequested()   { return TopicBuilder.name(KafkaTopics.PAYMENT_REQUESTED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic paymentCompleted()   { return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic paymentFailed()      { return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic reservationConfirmed(){ return TopicBuilder.name(KafkaTopics.RESERVATION_CONFIRMED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic reservationCancelled(){ return TopicBuilder.name(KafkaTopics.RESERVATION_CANCELLED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic notificationSend()   { return TopicBuilder.name(KafkaTopics.NOTIFICATION_SEND).partitions(3).replicas(1).build(); }
  @Bean public NewTopic queueEntered()       { return TopicBuilder.name(KafkaTopics.QUEUE_ENTERED).partitions(3).replicas(1).build(); }
  @Bean public NewTopic queueActivated()     { return TopicBuilder.name(KafkaTopics.QUEUE_ACTIVATED).partitions(3).replicas(1).build(); }
}