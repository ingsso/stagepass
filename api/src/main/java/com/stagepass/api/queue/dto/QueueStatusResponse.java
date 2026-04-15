package com.stagepass.api.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueStatusResponse {
  private Long rank;
  private Long total;
  private Long estimatedWaitSeconds; // 예상 대기 시간
  private String status; // WAITING / ACTIVATED / NOT_IN_QUEUE
}