package com.stagepass.domain.performance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @Column(nullable = false)
  private String name;     // VIP, R석, S석, A석

  @Column(nullable = false)
  private String grade;

  @Column(nullable = false)
  private Integer price;

  private Integer totalSeats;
  private Integer rowCount;
  private Integer colCount;

  @Builder
  public Zone(Show show, String name, String grade, Integer price,
              Integer rowCount, Integer colCount) {
    this.show = show;
    this.name = name;
    this.grade = grade;
    this.price = price;
    this.rowCount = rowCount;
    this.colCount = colCount;
    this.totalSeats = rowCount * colCount;
  }
}