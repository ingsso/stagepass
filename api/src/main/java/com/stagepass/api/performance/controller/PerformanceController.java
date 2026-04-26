package com.stagepass.api.performance.controller;

import com.stagepass.api.performance.dto.*;
import com.stagepass.api.performance.service.PerformanceService;
import com.stagepass.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "공연", description = "공연 목록 조회, 상세 조회, 회차 조회 API")
@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {

  private final PerformanceService performanceService;

  @Operation(summary = "공연 목록 조회", description = "전체 공연 목록을 조회합니다. keyword 파라미터로 공연명 검색이 가능합니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<PerformanceResponse>>> getAll(
      @Parameter(description = "공연명 검색어 (선택)") @RequestParam(required = false) String keyword,
      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    Page<PerformanceResponse> result = (keyword != null)
        ? performanceService.search(keyword, pageable)
        : performanceService.getAll(pageable);
    return ResponseEntity.ok(ApiResponse.ok(result));
  }

  @Operation(summary = "공연 상세 조회", description = "공연 ID로 특정 공연의 상세 정보를 조회합니다.")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PerformanceResponse>> getOne(
      @Parameter(description = "공연 ID") @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.getOne(id)));
  }

  @Operation(summary = "회차 목록 조회", description = "특정 공연의 회차(날짜/시간) 목록을 조회합니다.")
  @GetMapping("/{id}/shows")
  public ResponseEntity<ApiResponse<List<ShowResponse>>> getShows(
      @Parameter(description = "공연 ID") @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.getShows(id)));
  }

  @Operation(summary = "공연 등록", description = "새로운 공연을 등록합니다. (어드민 전용)")
  @PostMapping
  public ResponseEntity<ApiResponse<PerformanceResponse>> create(
      @Valid @RequestBody PerformanceRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.create(request)));
  }

  @Operation(summary = "공연 수정", description = "공연 정보를 수정합니다. (어드민 전용)")
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PerformanceResponse>> update(
      @Parameter(description = "공연 ID") @PathVariable Long id,
      @Valid @RequestBody PerformanceRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.update(id, request)));
  }

  @Operation(summary = "공연 삭제", description = "공연을 삭제합니다. (어드민 전용)")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @Parameter(description = "공연 ID") @PathVariable Long id) {
    performanceService.delete(id);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(summary = "회차 등록", description = "특정 공연에 새로운 회차를 추가합니다. (어드민 전용)")
  @PostMapping("/{id}/shows")
  public ResponseEntity<ApiResponse<ShowResponse>> createShow(
      @Parameter(description = "공연 ID") @PathVariable Long id,
      @Valid @RequestBody ShowRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(performanceService.createShow(id, request)));
  }
}
