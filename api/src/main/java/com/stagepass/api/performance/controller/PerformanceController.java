package com.stagepass.api.performance.controller;

import com.stagepass.api.performance.dto.*;
import com.stagepass.api.performance.service.PerformanceService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {

  private final PerformanceService performanceService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<PerformanceResponse>>> getAll(
      @RequestParam(required = false) String keyword) {
    List<PerformanceResponse> result = (keyword != null)
        ? performanceService.search(keyword)
        : performanceService.getAll();
    return ResponseEntity.ok(ApiResponse.ok(result));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PerformanceResponse>> getOne(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.getOne(id)));
  }

  @GetMapping("/{id}/shows")
  public ResponseEntity<ApiResponse<List<ShowResponse>>> getShows(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.getShows(id)));
  }

  // 어드민 전용 — 실제로는 admin 모듈로 분리, 여기선 임시 제공
  @PostMapping
  public ResponseEntity<ApiResponse<PerformanceResponse>> create(
      @RequestBody PerformanceRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.create(request)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PerformanceResponse>> update(
      @PathVariable Long id, @RequestBody PerformanceRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    performanceService.delete(id);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @PostMapping("/{id}/shows")
  public ResponseEntity<ApiResponse<ShowResponse>> createShow(
      @PathVariable Long id, @RequestBody ShowRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.createShow(id, request)));
  }
}