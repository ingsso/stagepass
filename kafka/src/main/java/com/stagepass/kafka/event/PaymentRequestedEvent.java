package com.stagepass.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestedEvent {
  private Long reservationId;
  private Long userId;
  private Integer amount;
  private String tossOrderId;
  private String paymentKey;
}