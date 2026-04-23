package com.stagepass.domain.waitlist;

import com.stagepass.domain.common.BaseEntity;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "waitlist_entries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitlistEntry extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WaitlistStatus status;

  private LocalDateTime notifiedAt;
  private LocalDateTime notifyExpiresAt; // 알림 후 10분 내 예매해야

  @Builder
  public WaitlistEntry(Show show, User user) {
    this.show = show;
    this.user = user;
    this.status = WaitlistStatus.WAITING;
  }

  public void notify(LocalDateTime now) {
    this.status = WaitlistStatus.NOTIFIED;
    this.notifiedAt = now;
    this.notifyExpiresAt = now.plusMinutes(10);
  }

  public void complete() {
    this.status = WaitlistStatus.RESERVED;
  }

  public void expire() {
    this.status = WaitlistStatus.EXPIRED;
  }

  public void cancel() {
    this.status = WaitlistStatus.CANCELLED;
  }
}
