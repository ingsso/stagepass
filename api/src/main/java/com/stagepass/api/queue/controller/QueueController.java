package com.stagepass.api.queue.controller;

import com.stagepass.api.queue.dto.QueueEnterResponse;
import com.stagepass.api.queue.dto.QueueStatusResponse;
import com.stagepass.api.queue.service.QueueService;
import com.stagepass.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "대기열", description = "티켓 구매 대기열 진입·조회·퇴장 API")
@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class QueueController {

  private final QueueService queueService;

  @Operation(summary = "대기열 진입", description = "특정 회차의 대기열에 진입합니다. Redis Sorted Set으로 순번이 관리됩니다.")
  @PostMapping("/{showId}/queue")
  public ResponseEntity<ApiResponse<QueueEnterResponse>> enter(
      @Parameter(description = "회차 ID") @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(queueService.enter(showId, userId)));
  }

  @Operation(summary = "대기열 순번 조회", description = "현재 대기 순번과 앞에 대기 중인 인원 수를 조회합니다.")
  @GetMapping("/{showId}/queue/status")
  public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
      @Parameter(description = "회차 ID") @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(queueService.getStatus(showId, userId)));
  }

  @Operation(summary = "대기열 퇴장", description = "대기열에서 나갑니다.")
  @DeleteMapping("/{showId}/queue")
  public ResponseEntity<ApiResponse<Void>> leave(
      @Parameter(description = "회차 ID") @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    queueService.leave(showId, userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}