package com.stagepass.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
  private Long userId;
  private String type;      // RESERVATION_CONFIRMED, PAYMENT_FAILED, QUEUE_ACTIVATED
  private String message;
}