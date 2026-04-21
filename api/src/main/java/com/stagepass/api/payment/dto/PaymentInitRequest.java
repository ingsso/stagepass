package com.stagepass.api.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentInitRequest {
  private Long reservationId;
}