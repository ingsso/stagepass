package com.stagepass.admin.dashboard.controller;

import com.stagepass.admin.dashboard.dto.DashboardResponse;
import com.stagepass.admin.dashboard.service.DashboardService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping
  public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
    return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboard()));
  }
}