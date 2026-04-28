package com.stagepass.admin.performance.service;

import com.stagepass.admin.performance.dto.AdminShowResponse;
import com.stagepass.admin.performance.dto.ZoneRequest;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPerformanceService {

  private final ShowRepository showRepository;
  private final ZoneRepository zoneRepository;
  private final SeatRepository seatRepository;

  // 회차별 예매 현황 — 집계 쿼리 1회 (N+1 제거)
  @Transactional(readOnly = true)
  public List<AdminShowResponse> getShowStats(Long performanceId) {
    return showRepository.findWithConfirmedReservationCount(performanceId).stream()
        .map(row -> new AdminShowResponse((Show) row[0], ((Long) row[1]).intValue()))
        .toList();
  }

  // 구역 + 좌석 일괄 생성
  @Transactional
  public void createZoneWithSeats(Long showId, ZoneRequest request) {
    Show show = showRepository.findById(showId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOW_NOT_FOUND));

    Zone zone = Zone.builder()
        .show(show)
        .name(request.getName())
        .grade(request.getGrade())
        .price(request.getPrice())
        .rowCount(request.getRowCount())
        .colCount(request.getColCount())
        .build();
    zoneRepository.save(zone);

    // 좌석 자동 생성 (R-01-01 형식)
    for (int row = 1; row <= request.getRowCount(); row++) {
      for (int col = 1; col <= request.getColCount(); col++) {
        String seatCode = String.format("%s-%02d-%02d", request.getGrade(), row, col);
        seatRepository.save(
            Seat.builder()
                .zone(zone)
                .seatCode(seatCode)
                .rowNum(row)
                .colNum(col)
                .build()
        );
      }
    }

    log.info("[Admin] 구역·좌석 생성 showId={} zone={} seats={}",
        showId, request.getName(), request.getRowCount() * request.getColCount());
  }

  // 회차 상태 변경 (ON_SALE, CANCELLED 등)
  @Transactional
  public void updateShowStatus(Long showId, String status) {
    Show show = showRepository.findById(showId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOW_NOT_FOUND));
    show.updateStatus(ShowStatus.valueOf(status));
    log.info("[Admin] 회차 상태 변경 showId={} status={}", showId, status);
  }
}