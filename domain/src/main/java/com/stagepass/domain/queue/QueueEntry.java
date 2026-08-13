// domain/src/main/java/com/stagepass/domain/queue/QueueEntry.java
package com.stagepass.domain.queue;

import com.stagepass.domain.common.BaseEntity;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "queue_entries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "user_id"}),
    indexes = {
        @Index(name = "idx_queue_entries_show_id_status", columnList = "show_id, status"),
        @Index(name = "idx_queue_entries_show_id_user_id", columnList = "show_id, user_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueEntry extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  private Integer rank;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private QueueStatus status;

  private LocalDateTime enteredAt;
  private LocalDateTime activatedAt;

  @Builder
  public QueueEntry(Show show, User user, Integer rank) {
    this.show = show;
    this.user = user;
    this.rank = rank;
    this.status = QueueStatus.WAITING;
    this.enteredAt = LocalDateTime.now();
  }

  public void activate() {
    this.status = QueueStatus.ACTIVATED;
    this.activatedAt = LocalDateTime.now();
  }
}