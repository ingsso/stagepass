package com.stagepass.api.exchange.dto;

import com.stagepass.domain.exchange.SeatExchange;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ExchangeResponse {
  private final Long exchangeId;
  private final Long proposerId;
  private final String proposerName;
  private final Long receiverId;
  private final String receiverName;
  private final String proposerSeatInfo;   // 공연명 + 좌석코드
  private final String receiverSeatInfo;
  private final String status;
  private final LocalDateTime expiresAt;

  public ExchangeResponse(SeatExchange e) {
    this.exchangeId = e.getId();
    this.proposerId = e.getProposer().getId();
    this.proposerName = e.getProposer().getName();
    this.receiverId = e.getReceiver().getId();
    this.receiverName = e.getReceiver().getName();

    var pr = e.getProposerReservation();
    this.proposerSeatInfo = pr.getShow().getPerformance().getTitle()
        + " / " + pr.getReservationSeats().stream()
            .map(rs -> rs.getSeat().getSeatCode())
            .reduce((a, b) -> a + ", " + b).orElse("-");

    var rr = e.getReceiverReservation();
    this.receiverSeatInfo = rr.getShow().getPerformance().getTitle()
        + " / " + rr.getReservationSeats().stream()
            .map(rs -> rs.getSeat().getSeatCode())
            .reduce((a, b) -> a + ", " + b).orElse("-");

    this.status = e.getStatus().name();
    this.expiresAt = e.getExpiresAt();
  }
}
