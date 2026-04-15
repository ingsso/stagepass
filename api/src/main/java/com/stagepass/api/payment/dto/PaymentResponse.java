package com.stagepass.api.payment.dto;

import com.stagepass.domain.payment.Payment;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentResponse {
  private final Long id;
  private final Long reservationId;
  private final String tossOrderId;
  private final Integer amount;
  private final String status;
  private final String method;
  private final LocalDateTime paidAt;

  public PaymentResponse(Payment p) {
    this.id = p.getId();
    this.reservationId = p.getReservation().getId();
    this.tossOrderId = p.getTossOrderId();
    this.amount = p.getAmount();
    this.status = p.getStatus().name();
    this.method = p.getMethod();
    this.paidAt = p.getPaidAt();
  }
}