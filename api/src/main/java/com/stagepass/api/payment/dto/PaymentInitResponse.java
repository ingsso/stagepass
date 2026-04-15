package com.stagepass.api.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentInitResponse {
  private String orderId;      // tossOrderId — 프론트에서 토스 위젯에 전달
  private Integer amount;
  private String orderName;    // 공연명 + 좌석
}