package com.stagepass.admin.performance.dto;

import com.stagepass.domain.performance.Show;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AdminShowResponse {
  private final Long id;
  private final LocalDateTime showDatetime;
  private final Integer totalSeats;
  private final Integer availableSeats;
  private final String status;
  private final int reservationCount;
  private final double reservationRate;

  public AdminShowResponse(Show show, int reservationCount) {
    this.id = show.getId();
    this.showDatetime = show.getShowDatetime();
    this.totalSeats = show.getTotalSeats();
    this.availableSeats = show.getAvailableSeats();
    this.status = show.getStatus().name();
    this.reservationCount = reservationCount;
    this.reservationRate = show.getTotalSeats() > 0
        ? (double) reservationCount / show.getTotalSeats() * 100 : 0;
  }
}