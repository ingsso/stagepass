package com.stagepass.api.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentInitRequest {

  @NotNull(message = "예매 ID는 필수입니다.")
  private Long reservationId;
}