package com.stagepass.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent {
  private Long transferId;
  private Long fromUserId;
  private Long toUserId;
}
