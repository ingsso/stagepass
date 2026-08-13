package com.stagepass.admin.performance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공연 등록 요청.
 *
 * 검증 규칙은 API 서버의 PerformanceRequest 와 일치시킨다 —
 * 같은 리소스를 만드는 두 경로가 서로 다른 규칙을 갖지 않도록 한다.
 * (Performance.title 은 DB NOT NULL 이므로, 검증이 없으면 500 으로 떨어진다)
 */
@Getter
@NoArgsConstructor
public class PerformanceCreateRequest {

    @NotBlank(message = "공연 제목은 필수입니다.")
    private String title;

    @NotBlank(message = "장르는 필수입니다.")
    private String genre;

    private String description;
    private String posterUrl;

    @NotBlank(message = "공연장 이름은 필수입니다.")
    private String venueName;

    private String venueAddress;
    private Integer runningTime;

    /** 함께 생성할 회차 시각 목록 (없으면 공연만 생성) */
    private List<LocalDateTime> showDatetimes;
}
