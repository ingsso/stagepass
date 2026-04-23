package com.stagepass.api.waitlist.controller;

import com.stagepass.api.waitlist.dto.WaitlistStatusResponse;
import com.stagepass.api.waitlist.service.WaitlistService;
import com.stagepass.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "취소 대기", description = "매진 공연 취소 대기 API")
@RestController
@RequestMapping("/api/shows/{showId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

  private final WaitlistService waitlistService;

  @Operation(summary = "취소 대기 등록", description = "매진된 공연 회차의 취소 대기열에 등록합니다. 현재 순번을 반환합니다.")
  @PostMapping
  public ApiResponse<WaitlistStatusResponse> join(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(waitlistService.join(showId, userId));
  }

  @Operation(summary = "취소 대기 순번 조회", description = "현재 대기 순번과 전체 대기 인원을 조회합니다.")
  @GetMapping
  public ApiResponse<WaitlistStatusResponse> getStatus(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.ok(waitlistService.getStatus(showId, userId));
  }

  @Operation(summary = "취소 대기 이탈", description = "취소 대기열에서 이탈합니다.")
  @DeleteMapping
  public ApiResponse<Void> leave(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId) {
    waitlistService.leave(showId, userId);
    return ApiResponse.ok();
  }
}
