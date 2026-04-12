package com.stagepass.domain.reservation;

import com.stagepass.domain.common.BaseEntity;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status;

  @Column(nullable = false)
  private Integer totalPrice;

  private LocalDateTime reservedAt;
  private LocalDateTime expiresAt;   // 선점 만료 시각

  @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
  private List<ReservationSeat> reservationSeats = new ArrayList<>();

  @Builder
  public Reservation(User user, Show show, Integer totalPrice) {
    this.user = user;
    this.show = show;
    this.totalPrice = totalPrice;
    this.status = ReservationStatus.PENDING;
    this.reservedAt = LocalDateTime.now();
    this.expiresAt = LocalDateTime.now().plusMinutes(5);
  }

  public void confirm() {
    this.status = ReservationStatus.CONFIRMED;
  }

  public void cancel() {
    this.status = ReservationStatus.CANCELLED;
  }

  public void expire() {
    this.status = ReservationStatus.EXPIRED;
  }
}