package com.stagepass.api.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentConfirmRequest {
  private Long reservationId;
  private String paymentKey;
  private String orderId;
  private Integer amount;
}
