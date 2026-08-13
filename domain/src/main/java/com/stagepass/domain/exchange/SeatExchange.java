package com.stagepass.domain.exchange;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.common.BaseEntity;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_exchanges", indexes = {
    @Index(name = "idx_seat_exchanges_status_expires_at", columnList = "status, expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatExchange extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "proposer_id", nullable = false)
  private User proposer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  // 제안자의 예매 (교환 후 receiver 소유가 됨)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "proposer_reservation_id", nullable = false)
  private Reservation proposerReservation;

  // 수락자의 예매 (교환 후 proposer 소유가 됨)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receiver_reservation_id", nullable = false)
  private Reservation receiverReservation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SeatExchangeStatus status;

  private LocalDateTime expiresAt;
  private LocalDateTime completedAt;

  @Builder
  public SeatExchange(User proposer, User receiver,
                      Reservation proposerReservation, Reservation receiverReservation) {
    this.proposer = proposer;
    this.receiver = receiver;
    this.proposerReservation = proposerReservation;
    this.receiverReservation = receiverReservation;
    this.status = SeatExchangeStatus.PENDING;
    this.expiresAt = LocalDateTime.now().plusHours(24);
  }

  public void accept() {
    requirePending();
    this.status = SeatExchangeStatus.ACCEPTED;
    this.completedAt = LocalDateTime.now();
  }

  public void reject() {
    requirePending();
    this.status = SeatExchangeStatus.REJECTED;
  }

  public void cancel() {
    requirePending();
    this.status = SeatExchangeStatus.CANCELLED;
  }

  public void expire() {
    if (this.status != SeatExchangeStatus.PENDING) return; // 멱등 처리
    this.status = SeatExchangeStatus.EXPIRED;
  }

  private void requirePending() {
    if (this.status != SeatExchangeStatus.PENDING) {
      throw new BusinessException(ErrorCode.EXCHANGE_NOT_PENDING);
    }
  }
}
