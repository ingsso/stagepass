package com.stagepass.api.performance.service;

import com.stagepass.api.performance.dto.*;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceService {

  private final PerformanceRepository performanceRepository;
  private final ShowRepository showRepository;

  // 공연 목록
  @Transactional(readOnly = true)
  public List<PerformanceResponse> getAll() {
    return performanceRepository.findAll().stream()
        .map(PerformanceResponse::new)
        .toList();
  }

  // 공연 검색
  @Transactional(readOnly = true)
  public List<PerformanceResponse> search(String keyword) {
    return performanceRepository.searchByTitle(keyword).stream()
        .map(PerformanceResponse::new)
        .toList();
  }

  // 공연 상세
  @Transactional(readOnly = true)
  public PerformanceResponse getOne(Long id) {
    return new PerformanceResponse(findPerformance(id));
  }

  // 공연 등록 (어드민)
  @Transactional
  public PerformanceResponse create(PerformanceRequest request) {
    Performance performance = Performance.builder()
        .title(request.getTitle())
        .genre(request.getGenre())
        .description(request.getDescription())
        .posterUrl(request.getPosterUrl())
        .venueName(request.getVenueName())
        .venueAddress(request.getVenueAddress())
        .runningTime(request.getRunningTime())
        .build();
    return new PerformanceResponse(performanceRepository.save(performance));
  }

  // 공연 수정 (어드민)
  @Transactional
  public PerformanceResponse update(Long id, PerformanceRequest request) {
    Performance performance = findPerformance(id);
    performance.update(
        request.getTitle(), request.getGenre(), request.getDescription(),
        request.getPosterUrl(), request.getVenueName(), request.getVenueAddress(),
        request.getRunningTime()
    );
    return new PerformanceResponse(performance);
  }

  // 공연 삭제 (어드민)
  @Transactional
  public void delete(Long id) {
    performanceRepository.delete(findPerformance(id));
  }

  // 회차 목록
  @Transactional(readOnly = true)
  public List<ShowResponse> getShows(Long performanceId) {
    return showRepository.findByPerformanceId(performanceId).stream()
        .map(ShowResponse::new)
        .toList();
  }

  // 회차 등록 (어드민)
  @Transactional
  public ShowResponse createShow(Long performanceId, ShowRequest request) {
    Performance performance = findPerformance(performanceId);
    Show show = Show.builder()
        .performance(performance)
        .showDatetime(request.getShowDatetime())
        .totalSeats(request.getTotalSeats())
        .status(ShowStatus.SCHEDULED)
        .build();
    return new ShowResponse(showRepository.save(show));
  }

  private Performance findPerformance(Long id) {
    return performanceRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
  }
}