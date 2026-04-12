package com.stagepass.domain.performance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seats",
    uniqueConstraints = @UniqueConstraint(columnNames = {"zone_id", "seat_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "zone_id", nullable = false)
  private Zone zone;

  @Column(nullable = false)
  private String seatCode;   // R-01-05

  private Integer rowNum;
  private Integer colNum;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SeatStatus status;

  @Builder
  public Seat(Zone zone, String seatCode, Integer rowNum, Integer colNum) {
    this.zone = zone;
    this.seatCode = seatCode;
    this.rowNum = rowNum;
    this.colNum = colNum;
    this.status = SeatStatus.AVAILABLE;
  }

  public void reserve() {
    this.status = SeatStatus.RESERVED;
  }

  public void release() {
    this.status = SeatStatus.AVAILABLE;
  }

  public void block() {
    this.status = SeatStatus.BLOCKED;
  }
}