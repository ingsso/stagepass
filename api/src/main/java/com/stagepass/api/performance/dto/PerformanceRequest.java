package com.stagepass.api.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PerformanceRequest {

  @NotBlank(message = "공연 제목은 필수입니다.")
  private String title;

  @NotBlank(message = "장르는 필수입니다.")
  private String genre;

  private String description;
  private String posterUrl;

  @NotBlank(message = "공연장 이름은 필수입니다.")
  private String venueName;

  private String venueAddress;

  @Positive(message = "러닝타임은 0보다 커야 합니다.")
  private Integer runningTime;
}