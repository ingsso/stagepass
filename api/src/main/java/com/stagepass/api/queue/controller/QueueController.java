package com.stagepass.api.queue.controller;

import com.stagepass.api.queue.dto.QueueEnterResponse;
import com.stagepass.api.queue.dto.QueueStatusResponse;
import com.stagepass.api.queue.service.QueueService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class QueueController {

  private final QueueService queueService;

  // 대기열 진입
  @PostMapping("/{showId}/queue")
  public ResponseEntity<ApiResponse<QueueEnterResponse>> enter(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(queueService.enter(showId, userId)));
  }

  // 대기열 순번 조회
  @GetMapping("/{showId}/queue/status")
  public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(queueService.getStatus(showId, userId)));
  }

  // 대기열 퇴장
  @DeleteMapping("/{showId}/queue")
  public ResponseEntity<ApiResponse<Void>> leave(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    queueService.leave(showId, userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}