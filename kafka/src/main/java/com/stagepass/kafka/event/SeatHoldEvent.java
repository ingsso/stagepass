package com.stagepass.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldEvent {
  private Long seatId;
  private Long userId;
  private Long reservationId;
  private Long expiresAt;   // epoch millis
}