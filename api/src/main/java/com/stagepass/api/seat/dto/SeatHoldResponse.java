package com.stagepass.api.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SeatHoldResponse {
  private List<Long> heldSeatIds;
  private List<Long> failedSeatIds;
  private Long reservationId;
  private Long expiresAt; // epoch millis
}