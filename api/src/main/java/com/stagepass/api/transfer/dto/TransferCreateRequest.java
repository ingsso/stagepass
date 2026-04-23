package com.stagepass.api.transfer.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TransferCreateRequest {
  private Long reservationId;
}
