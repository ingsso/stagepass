package com.stagepass.admin.performance.service;

import com.stagepass.admin.performance.dto.PerformanceCreateRequest;
import com.stagepass.admin.performance.dto.PerformanceResponse;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.PerformanceRepository;
import com.stagepass.domain.performance.SeatRepository;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.performance.ShowStatus;
import com.stagepass.domain.performance.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminPerformanceServiceTest {

  @InjectMocks
  private AdminPerformanceService adminPerformanceService;

  @Mock private PerformanceRepository performanceRepository;
  @Mock private ShowRepository showRepository;
  @Mock private ZoneRepository zoneRepository;
  @Mock private SeatRepository seatRepository;

  private PerformanceCreateRequest request(LocalDateTime... showDatetimes) {
    PerformanceCreateRequest request = new PerformanceCreateRequest();
    ReflectionTestUtils.setField(request, "title", "뮤지컬 테스트");
    ReflectionTestUtils.setField(request, "genre", "MUSICAL");
    ReflectionTestUtils.setField(request, "venueName", "테스트 공연장");
    ReflectionTestUtils.setField(request, "runningTime", 120);
    if (showDatetimes.length > 0) {
      ReflectionTestUtils.setField(request, "showDatetimes", List.of(showDatetimes));
    }
    return request;
  }

  private Performance savedPerformance() {
    Performance performance = Performance.builder()
        .title("뮤지컬 테스트")
        .genre("MUSICAL")
        .venueName("테스트 공연장")
        .runningTime(120)
        .build();
    ReflectionTestUtils.setField(performance, "id", 1L);
    return performance;
  }

  @Test
  @DisplayName("공연 등록 — 회차 목록과 함께 생성하면 회차가 SCHEDULED 상태로 저장된다")
  void createPerformance_회차포함() {
    LocalDateTime first = LocalDateTime.of(2026, 12, 15, 19, 0);
    LocalDateTime second = LocalDateTime.of(2026, 12, 16, 19, 0);
    Performance performance = savedPerformance();

    given(performanceRepository.save(any(Performance.class))).willReturn(performance);
    given(showRepository.save(any(Show.class))).willAnswer(invocation -> invocation.getArgument(0));

    PerformanceResponse response = adminPerformanceService.createPerformance(request(first, second));

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getTitle()).isEqualTo("뮤지컬 테스트");
    assertThat(response.getShows()).hasSize(2);
    assertThat(response.getShows())
        .extracting(PerformanceResponse.ShowSummary::getShowDatetime)
        .containsExactly(first, second);
    assertThat(response.getShows())
        .extracting(PerformanceResponse.ShowSummary::getStatus)
        .containsOnly(ShowStatus.SCHEDULED.name());
  }

  @Test
  @DisplayName("공연 등록 — 회차 목록이 없으면 공연만 생성하고 회차는 저장하지 않는다")
  void createPerformance_회차없음() {
    given(performanceRepository.save(any(Performance.class))).willReturn(savedPerformance());

    PerformanceResponse response = adminPerformanceService.createPerformance(request());

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getShows()).isEmpty();
    then(showRepository).should(never()).save(any());
  }
}
