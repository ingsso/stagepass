package com.stagepass.api.exchange.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ExchangeRequest {

  @NotNull(message = "내 예매 ID는 필수입니다.")
  private Long myReservationId;

  @NotNull(message = "상대방 예매 ID는 필수입니다.")
  private Long targetReservationId;
}
