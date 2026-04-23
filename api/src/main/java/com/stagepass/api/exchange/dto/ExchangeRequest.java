package com.stagepass.api.exchange.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExchangeRequest {
  private Long myReservationId;       // 내가 내놓을 예매
  private Long targetReservationId;   // 상대방의 예매
}
