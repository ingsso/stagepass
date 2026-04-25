package com.stagepass.api.seat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SeatHoldRequest {

  @NotEmpty(message = "좌석을 1개 이상 선택해야 합니다.")
  @Size(max = 4, message = "최대 4석까지 선택할 수 있습니다.")
  private List<Long> seatIds;
}