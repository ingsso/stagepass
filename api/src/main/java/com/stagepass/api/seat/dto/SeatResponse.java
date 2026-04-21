package com.stagepass.api.seat.dto;

import com.stagepass.domain.performance.Seat;
import lombok.Getter;

@Getter
public class SeatResponse {
  private final Long id;
  private final String seatCode;
  private final Integer rowNum;
  private final Integer colNum;
  private final String status;
  private final String grade;
  private final Integer price;
  private final Long remainingSeconds; // Redis TTL

  public SeatResponse(Seat seat, Long remainingSeconds) {
    this.id = seat.getId();
    this.seatCode = seat.getSeatCode();
    this.rowNum = seat.getRowNum();
    this.colNum = seat.getColNum();
    this.status = seat.getStatus().name();
    this.grade = seat.getZone().getGrade();
    this.price = seat.getZone().getPrice();
    this.remainingSeconds = remainingSeconds;
  }
}