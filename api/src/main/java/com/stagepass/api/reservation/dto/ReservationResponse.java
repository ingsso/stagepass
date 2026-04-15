package com.stagepass.api.reservation.dto;

import com.stagepass.domain.reservation.Reservation;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ReservationResponse {
  private final Long id;
  private final Long showId;
  private final String performanceTitle;
  private final LocalDateTime showDatetime;
  private final String status;
  private final Integer totalPrice;
  private final LocalDateTime reservedAt;
  private final LocalDateTime expiresAt;
  private final List<String> seatCodes;

  public ReservationResponse(Reservation r) {
    this.id = r.getId();
    this.showId = r.getShow().getId();
    this.performanceTitle = r.getShow().getPerformance().getTitle();
    this.showDatetime = r.getShow().getShowDatetime();
    this.status = r.getStatus().name();
    this.totalPrice = r.getTotalPrice();
    this.reservedAt = r.getReservedAt();
    this.expiresAt = r.getExpiresAt();
    this.seatCodes = r.getReservationSeats().stream()
        .map(rs -> rs.getSeat().getSeatCode())
        .toList();
  }
}