package com.stagepass.domain.performance;

import com.stagepass.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Show extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "performance_id", nullable = false)
  private Performance performance;

  @Column(nullable = false)
  private LocalDateTime showDatetime;

  private Integer totalSeats;
  private Integer availableSeats;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ShowStatus status;

  @Builder
  public Show(Performance performance, LocalDateTime showDatetime,
              Integer totalSeats, ShowStatus status) {
    this.performance = performance;
    this.showDatetime = showDatetime;
    this.totalSeats = totalSeats;
    this.availableSeats = totalSeats;
    this.status = status;
  }

  public void decreaseAvailableSeats(int count) {
    if (this.availableSeats < count) {
      throw new IllegalStateException("잔여 좌석이 부족합니다.");
    }
    this.availableSeats -= count;
  }

  public void increaseAvailableSeats(int count) {
    this.availableSeats += count;
  }

  public void updateStatus(ShowStatus status) {
    this.status = status;
  }
}