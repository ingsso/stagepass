package com.stagepass.api.performance.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PerformanceRequest {
  private String title;
  private String genre;
  private String description;
  private String posterUrl;
  private String venueName;
  private String venueAddress;
  private Integer runningTime;
}