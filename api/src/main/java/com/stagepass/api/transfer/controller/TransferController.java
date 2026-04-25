package com.stagepass.api.transfer.controller;

import jakarta.validation.Valid;
import com.stagepass.api.transfer.dto.TransferCreateRequest;
import jakarta.validation.Valid;
import com.stagepass.api.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import com.stagepass.api.transfer.service.TransferService;
import jakarta.validation.Valid;
import com.stagepass.common.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "양도", description = "티켓 양도 게시판 API")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

  private final TransferService transferService;

  @Operation(summary = "양도 게시글 등록")
  @PostMapping
  public ApiResponse<TransferResponse> create(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody TransferCreateRequest request) {
    return ApiResponse.ok(transferService.create(userId, request));
  }

  @Operation(summary = "양도 게시판 목록 조회", description = "showId 파라미터로 특정 회차 필터링 가능")
  @GetMapping
  public ApiResponse<List<TransferResponse>> getList(
      @RequestParam(required = false) Long showId) {
    return ApiResponse.ok(transferService.getList(showId));
  }

  @Operation(summary = "내 양도 글 목록")
  @GetMapping("/my")
  public ApiResponse<List<TransferResponse>> getMyTransfers(
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(transferService.getMyTransfers(userId));
  }

  @Operation(summary = "양도 수락 (선착순)")
  @PostMapping("/{transferId}/claim")
  public ApiResponse<TransferResponse> claim(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long transferId) {
    return ApiResponse.ok(transferService.claim(transferId, userId));
  }

  @Operation(summary = "양도 게시글 취소")
  @DeleteMapping("/{transferId}")
  public ApiResponse<Void> cancel(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long transferId) {
    transferService.cancel(transferId, userId);
    return ApiResponse.ok();
  }
}
