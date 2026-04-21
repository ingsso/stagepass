package com.stagepass.api.seat.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonCreator
  public SeatResponse(
      @JsonProperty("id") Long id,
      @JsonProperty("seatCode") String seatCode,
      @JsonProperty("rowNum") Integer rowNum,
      @JsonProperty("colNum") Integer colNum,
      @JsonProperty("status") String status,
      @JsonProperty("grade") String grade,
      @JsonProperty("price") Integer price,
      @JsonProperty("remainingSeconds") Long remainingSeconds) {
    this.id = id;
    this.seatCode = seatCode;
    this.rowNum = rowNum;
    this.colNum = colNum;
    this.status = status;
    this.grade = grade;
    this.price = price;
    this.remainingSeconds = remainingSeconds;
  }

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