package com.stagepass.domain.performance;

import com.stagepass.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "performances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  private String genre;

  @Column(columnDefinition = "TEXT")
  private String description;

  private String posterUrl;
  private String venueName;
  private String venueAddress;
  private Integer runningTime;   // 분 단위

  @Builder
  public Performance(String title, String genre, String description,
                     String posterUrl, String venueName, String venueAddress,
                     Integer runningTime) {
    this.title = title;
    this.genre = genre;
    this.description = description;
    this.posterUrl = posterUrl;
    this.venueName = venueName;
    this.venueAddress = venueAddress;
    this.runningTime = runningTime;
  }

  public void update(String title, String genre, String description,
                     String posterUrl, String venueName, String venueAddress,
                     Integer runningTime) {
    this.title = title;
    this.genre = genre;
    this.description = description;
    this.posterUrl = posterUrl;
    this.venueName = venueName;
    this.venueAddress = venueAddress;
    this.runningTime = runningTime;
  }
}