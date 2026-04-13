package com.stagepass.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossPaymentResponse {
  private String paymentKey;
  private String orderId;
  private String status;
  private Integer totalAmount;
  private String method;
  private String failureCode;
  private String failureMessage;
}