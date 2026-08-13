package com.stagepass.api.exchange.controller;

import jakarta.validation.Valid;
import com.stagepass.api.exchange.dto.ExchangeRequest;
import com.stagepass.api.exchange.dto.ExchangeResponse;
import com.stagepass.api.exchange.service.SeatExchangeService;
import com.stagepass.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "자리 교환", description = "같은 회차 내 좌석 교환 API")
@RestController
@RequestMapping("/api/exchanges")
@RequiredArgsConstructor
public class SeatExchangeController {

  private final SeatExchangeService exchangeService;

  @Operation(summary = "교환 제안", description = "같은 회차의 다른 유저에게 자리 교환을 제안합니다.")
  @PostMapping
  public ApiResponse<ExchangeResponse> propose(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ExchangeRequest request) {
    return ApiResponse.ok(exchangeService.propose(userId, request));
  }

  @Operation(summary = "받은 교환 제안 목록", description = "나에게 들어온 PENDING 상태의 교환 제안 목록을 조회합니다.")
  @GetMapping("/received")
  public ApiResponse<List<ExchangeResponse>> getReceived(
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(exchangeService.getReceivedProposals(userId));
  }

  @Operation(summary = "보낸 교환 제안 목록", description = "내가 보낸 교환 제안 목록을 조회합니다.")
  @GetMapping("/sent")
  public ApiResponse<List<ExchangeResponse>> getSent(
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(exchangeService.getSentProposals(userId));
  }

  @Operation(summary = "교환 수락", description = "교환 제안을 수락합니다. 두 예매의 소유자가 원자적으로 스왑됩니다.")
  @PostMapping("/{exchangeId}/accept")
  public ApiResponse<ExchangeResponse> accept(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long exchangeId) {
    return ApiResponse.ok(exchangeService.accept(exchangeId, userId));
  }

  @Operation(summary = "교환 거절", description = "교환 제안을 거절합니다.")
  @PostMapping("/{exchangeId}/reject")
  public ApiResponse<ExchangeResponse> reject(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long exchangeId) {
    return ApiResponse.ok(exchangeService.reject(exchangeId, userId));
  }

  @Operation(summary = "교환 제안 취소", description = "내가 보낸 교환 제안을 취소합니다.")
  @DeleteMapping("/{exchangeId}")
  public ApiResponse<Void> cancel(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long exchangeId) {
    exchangeService.cancel(exchangeId, userId);
    return ApiResponse.ok();
  }
}
