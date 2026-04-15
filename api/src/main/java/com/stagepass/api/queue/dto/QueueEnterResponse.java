package com.stagepass.api.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueEnterResponse {
  private Long rank;       // 현재 순번
  private Long total;      // 전체 대기 인원
  private boolean activated; // 즉시 입장 가능 여부
}