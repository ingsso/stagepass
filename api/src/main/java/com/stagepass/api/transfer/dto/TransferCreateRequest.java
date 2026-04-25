package com.stagepass.api.transfer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TransferCreateRequest {

  @NotNull(message = "예매 ID는 필수입니다.")
  private Long reservationId;
}
