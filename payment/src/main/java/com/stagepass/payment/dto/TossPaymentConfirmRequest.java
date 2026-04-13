package com.stagepass.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossPaymentConfirmRequest {
  private String paymentKey;
  private String orderId;
  private Integer amount;
}