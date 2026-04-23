package com.stagepass.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossPaymentCancelRequest {
  private String cancelReason;
  private Integer cancelAmount;
}
