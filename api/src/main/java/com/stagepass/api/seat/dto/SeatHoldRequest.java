package com.stagepass.api.seat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SeatHoldRequest {
  private List<Long> seatIds;
}