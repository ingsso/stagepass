package com.stagepass.api.performance.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ShowRequest {
  private LocalDateTime showDatetime;
  private Integer totalSeats;
}