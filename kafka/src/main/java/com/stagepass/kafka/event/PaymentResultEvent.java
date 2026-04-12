package com.stagepass.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultEvent {
  private Long reservationId;
  private Long userId;
  private String tossOrderId;
  private String reason;   // 실패 사유 (성공 시 null)
}