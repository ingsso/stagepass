package com.stagepass.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SeatExchangeEvent {
  private Long exchangeId;
  private Long proposerId;
  private Long receiverId;
}
