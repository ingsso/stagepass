package com.stagepass.api.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentConfirmRequest {

  @NotBlank(message = "paymentKey는 필수입니다.")
  private String paymentKey;

  @NotBlank(message = "orderId는 필수입니다.")
  private String orderId;

  @NotNull(message = "결제 금액은 필수입니다.")
  @Positive(message = "결제 금액은 0보다 커야 합니다.")
  private Integer amount;
}
