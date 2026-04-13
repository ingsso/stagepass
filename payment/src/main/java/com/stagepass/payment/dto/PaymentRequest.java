package com.stagepass.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentRequest {
  private Long reservationId;
  private String paymentKey;
  private String orderId;
  private Integer amount;
}