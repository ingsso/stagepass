package com.stagepass.api.transfer.dto;

import com.stagepass.domain.transfer.Transfer;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TransferResponse {
  private final Long transferId;
  private final Long reservationId;
  private final String performanceTitle;
  private final LocalDateTime showDatetime;
  private final String fromUserName;
  private final Integer totalPrice;
  private final String status;
  private final LocalDateTime expiresAt;

  public TransferResponse(Transfer transfer) {
    this.transferId = transfer.getId();
    this.reservationId = transfer.getReservation().getId();
    this.performanceTitle = transfer.getReservation().getShow().getPerformance().getTitle();
    this.showDatetime = transfer.getReservation().getShow().getShowDatetime();
    this.fromUserName = transfer.getFromUser().getName();
    this.totalPrice = transfer.getReservation().getTotalPrice();
    this.status = transfer.getStatus().name();
    this.expiresAt = transfer.getExpiresAt();
  }
}
