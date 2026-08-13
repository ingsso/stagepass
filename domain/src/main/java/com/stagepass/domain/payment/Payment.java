package com.stagepass.domain.payment;

import com.stagepass.domain.common.BaseEntity;
import com.stagepass.domain.reservation.Reservation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @Column(nullable = false, unique = true)
  private String tossOrderId;      // 멱등성 키

  private String tossPaymentKey;

  @Column(nullable = false)
  private Integer amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  private String method;           // 카드, 가상계좌 등

  @Column(length = 500)
  private String failureReason;    // 실패 사유 (Toss API 에러 메시지)

  private LocalDateTime paidAt;
  private LocalDateTime cancelledAt;

  @Builder
  public Payment(Reservation reservation, String tossOrderId, Integer amount) {
    this.reservation = reservation;
    this.tossOrderId = tossOrderId;
    this.amount = amount;
    this.status = PaymentStatus.PENDING;
  }

  public void complete(String tossPaymentKey, String method) {
    this.tossPaymentKey = tossPaymentKey;
    this.method = method;
    this.status = PaymentStatus.COMPLETED;
    this.paidAt = LocalDateTime.now();
  }

  public void fail(String reason) {
    this.status = PaymentStatus.FAILED;
    this.failureReason = reason;
  }

  public void cancel() {
    this.status = PaymentStatus.CANCELLED;
    this.cancelledAt = LocalDateTime.now();
  }
}