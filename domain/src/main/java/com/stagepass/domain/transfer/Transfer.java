package com.stagepass.domain.transfer;

import com.stagepass.domain.common.BaseEntity;
import com.stagepass.domain.reservation.Reservation;
import com.stagepass.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "from_user_id", nullable = false)
  private User fromUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "to_user_id")
  private User toUser;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransferStatus status;

  private LocalDateTime expiresAt;
  private LocalDateTime claimedAt;

  @Builder
  public Transfer(Reservation reservation, User fromUser, LocalDateTime expiresAt) {
    this.reservation = reservation;
    this.fromUser = fromUser;
    this.status = TransferStatus.OPEN;
    this.expiresAt = expiresAt;
  }

  public void claim(User toUser) {
    this.toUser = toUser;
    this.status = TransferStatus.CLAIMED;
    this.claimedAt = LocalDateTime.now();
  }

  public void cancel() {
    this.status = TransferStatus.CANCELLED;
  }

  public void expire() {
    this.status = TransferStatus.EXPIRED;
  }
}
