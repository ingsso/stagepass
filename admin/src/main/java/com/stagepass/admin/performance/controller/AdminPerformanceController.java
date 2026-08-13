package com.stagepass.admin.performance.controller;

import com.stagepass.admin.performance.dto.AdminShowResponse;
import com.stagepass.admin.performance.dto.PerformanceCreateRequest;
import com.stagepass.admin.performance.dto.PerformanceResponse;
import com.stagepass.admin.performance.dto.ZoneRequest;
import com.stagepass.admin.performance.service.AdminPerformanceService;
import com.stagepass.common.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "어드민 공연 관리", description = "회차 통계 조회, 구역·좌석 생성, 회차 상태 변경 API")
@RestController
@RequiredArgsConstructor
public class AdminPerformanceController {

  private final AdminPerformanceService adminPerformanceService;

  @Operation(summary = "공연 등록 (admin path)", description = "새 공연과 회차를 등록합니다.")
  @PostMapping("/admin/performances")
  public ResponseEntity<ApiResponse<PerformanceResponse>> createPerformance(
      @Valid @RequestBody PerformanceCreateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(adminPerformanceService.createPerformance(request)));
  }

  // 프론트가 /api/performances 경로로 호출하는 경우를 위한 별칭 (어드민 서버 8081 기준)
  @Operation(summary = "공연 등록 (api path alias)", description = "새 공연과 회차를 등록합니다.")
  @PostMapping("/api/performances")
  public ResponseEntity<ApiResponse<PerformanceResponse>> createPerformanceAlias(
      @Valid @RequestBody PerformanceCreateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(adminPerformanceService.createPerformance(request)));
  }

  @Operation(summary = "회차별 예매 현황 조회", description = "특정 공연의 회차별 예매 수, 잔여 좌석 등 현황을 조회합니다.")
  @GetMapping("/admin/performances/{performanceId}/shows/stats")
  public ResponseEntity<ApiResponse<List<AdminShowResponse>>> getShowStats(
      @Parameter(description = "공연 ID") @PathVariable Long performanceId) {
    return ResponseEntity.ok(ApiResponse.ok(
        adminPerformanceService.getShowStats(performanceId)));
  }

  @Operation(summary = "구역 및 좌석 일괄 생성", description = "특정 회차에 구역과 좌석을 일괄 생성합니다.")
  @PostMapping("/admin/performances/shows/{showId}/zones")
  public ResponseEntity<ApiResponse<Void>> createZone(
      @Parameter(description = "회차 ID") @PathVariable Long showId,
      @RequestBody ZoneRequest request) {
    adminPerformanceService.createZoneWithSeats(showId, request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(summary = "회차 상태 변경", description = "회차 상태를 변경합니다. (예: SCHEDULED → ON_SALE → CLOSED)")
  @PatchMapping("/admin/performances/shows/{showId}/status")
  public ResponseEntity<ApiResponse<Void>> updateShowStatus(
      @Parameter(description = "회차 ID") @PathVariable Long showId,
      @Parameter(description = "변경할 상태값") @RequestParam String status) {
    adminPerformanceService.updateShowStatus(showId, status);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}