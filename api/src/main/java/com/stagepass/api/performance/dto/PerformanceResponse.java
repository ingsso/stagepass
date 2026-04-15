package com.stagepass.api.performance.dto;

import com.stagepass.domain.performance.Performance;
import lombok.Getter;

@Getter
public class PerformanceResponse {
  private final Long id;
  private final String title;
  private final String genre;
  private final String description;
  private final String posterUrl;
  private final String venueName;
  private final String venueAddress;
  private final Integer runningTime;

  public PerformanceResponse(Performance p) {
    this.id = p.getId();
    this.title = p.getTitle();
    this.genre = p.getGenre();
    this.description = p.getDescription();
    this.posterUrl = p.getPosterUrl();
    this.venueName = p.getVenueName();
    this.venueAddress = p.getVenueAddress();
    this.runningTime = p.getRunningTime();
  }
}