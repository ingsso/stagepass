package com.stagepass.admin.dashboard.controller;

import com.stagepass.admin.dashboard.dto.DashboardResponse;
import com.stagepass.admin.dashboard.service.DashboardService;
import com.stagepass.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 대시보드", description = "예매·결제·공연 통계 대시보드 API")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @Operation(summary = "대시보드 통계 조회", description = "전체 예매 수, 결제 금액, 공연별 예매 현황 등 통계를 조회합니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
    return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboard()));
  }
}