package com.stagepass.admin.performance.controller;

import com.stagepass.admin.performance.dto.AdminShowResponse;
import com.stagepass.admin.performance.dto.ZoneRequest;
import com.stagepass.admin.performance.service.AdminPerformanceService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/performances")
@RequiredArgsConstructor
public class AdminPerformanceController {

  private final AdminPerformanceService adminPerformanceService;

  // 회차별 예매 현황
  @GetMapping("/{performanceId}/shows/stats")
  public ResponseEntity<ApiResponse<List<AdminShowResponse>>> getShowStats(
      @PathVariable Long performanceId) {
    return ResponseEntity.ok(ApiResponse.ok(
        adminPerformanceService.getShowStats(performanceId)));
  }

  // 구역 + 좌석 일괄 생성
  @PostMapping("/shows/{showId}/zones")
  public ResponseEntity<ApiResponse<Void>> createZone(
      @PathVariable Long showId,
      @RequestBody ZoneRequest request) {
    adminPerformanceService.createZoneWithSeats(showId, request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  // 회차 상태 변경
  @PatchMapping("/shows/{showId}/status")
  public ResponseEntity<ApiResponse<Void>> updateShowStatus(
      @PathVariable Long showId,
      @RequestParam String status) {
    adminPerformanceService.updateShowStatus(showId, status);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}