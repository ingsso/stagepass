package com.stagepass.api.performance.dto;

import com.stagepass.domain.performance.Show;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ShowResponse {
  private final Long id;
  private final Long performanceId;
  private final LocalDateTime showDatetime;
  private final Integer totalSeats;
  private final Integer availableSeats;
  private final String status;

  public ShowResponse(Show s) {
    this.id = s.getId();
    this.performanceId = s.getPerformance().getId();
    this.showDatetime = s.getShowDatetime();
    this.totalSeats = s.getTotalSeats();
    this.availableSeats = s.getAvailableSeats();
    this.status = s.getStatus().name();
  }
}