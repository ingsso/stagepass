package com.stagepass.api.performance.service;

import com.stagepass.api.performance.dto.PerformanceRequest;
import com.stagepass.api.performance.dto.PerformanceResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Performance;
import com.stagepass.domain.performance.PerformanceRepository;
import com.stagepass.domain.performance.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

  @InjectMocks private PerformanceService performanceService;

  @Mock private PerformanceRepository performanceRepository;
  @Mock private ShowRepository showRepository;

  private Performance performance;
  private static final Long PERF_ID = 1L;

  @BeforeEach
  void setUp() {
    performance = Performance.builder()
        .title("레미제라블")
        .genre("뮤지컬")
        .description("불굴의 명작")
        .posterUrl("https://example.com/poster.jpg")
        .venueName("블루스퀘어")
        .venueAddress("서울 용산구")
        .runningTime(180)
        .build();
    ReflectionTestUtils.setField(performance, "id", PERF_ID);
  }

  @Test
  @DisplayName("공연 목록 조회 — Page 반환")
  void getAll_페이지네이션_반환() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<Performance> page = new PageImpl<>(List.of(performance), pageable, 1);
    given(performanceRepository.findAll(pageable)).willReturn(page);

    Page<PerformanceResponse> result = performanceService.getAll(pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("레미제라블");
  }

  @Test
  @DisplayName("공연 검색 — keyword로 Page 반환")
  void search_키워드_결과반환() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<Performance> page = new PageImpl<>(List.of(performance), pageable, 1);
    given(performanceRepository.searchByTitle("레미", pageable)).willReturn(page);

    Page<PerformanceResponse> result = performanceService.search("레미", pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("레미제라블");
  }

  @Test
  @DisplayName("공연 상세 조회 — 존재하지 않으면 PERFORMANCE_NOT_FOUND 예외")
  void getOne_없는공연_예외() {
    given(performanceRepository.findById(PERF_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> performanceService.getOne(PERF_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.PERFORMANCE_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("공연 등록 — 저장 후 응답 반환")
  void create_공연등록_성공() {
    PerformanceRequest request = new PerformanceRequest();
    ReflectionTestUtils.setField(request, "title", "레미제라블");
    ReflectionTestUtils.setField(request, "genre", "뮤지컬");
    ReflectionTestUtils.setField(request, "description", "불굴의 명작");
    ReflectionTestUtils.setField(request, "posterUrl", "https://example.com/poster.jpg");
    ReflectionTestUtils.setField(request, "venueName", "블루스퀘어");
    ReflectionTestUtils.setField(request, "venueAddress", "서울 용산구");
    ReflectionTestUtils.setField(request, "runningTime", 180);

    given(performanceRepository.save(any(Performance.class))).willReturn(performance);

    PerformanceResponse response = performanceService.create(request);

    assertThat(response.getTitle()).isEqualTo("레미제라블");
    then(performanceRepository).should().save(any(Performance.class));
  }

  @Test
  @DisplayName("공연 삭제 — 없는 공연 삭제 시 예외")
  void delete_없는공연_예외() {
    given(performanceRepository.findById(PERF_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> performanceService.delete(PERF_ID))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(ErrorCode.PERFORMANCE_NOT_FOUND.getMessage());
  }
}
