package com.stagepass.api.performance.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ShowRequest {

  @NotNull(message = "공연 일시는 필수입니다.")
  @Future(message = "공연 일시는 미래여야 합니다.")
  private LocalDateTime showDatetime;

  @NotNull(message = "총 좌석 수는 필수입니다.")
  @Positive(message = "총 좌석 수는 0보다 커야 합니다.")
  private Integer totalSeats;
}