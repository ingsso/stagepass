package com.stagepass.api.seat.controller;

import jakarta.validation.Valid;
import com.stagepass.api.seat.dto.*;
import jakarta.validation.Valid;
import com.stagepass.api.seat.service.SeatService;
import jakarta.validation.Valid;
import com.stagepass.common.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "좌석", description = "좌석 조회 및 좌석 점유 API")
@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class SeatController {

  private final SeatService seatService;

  @Operation(summary = "좌석 목록 조회", description = "특정 회차의 전체 좌석과 점유 상태를 조회합니다.")
  @GetMapping("/{showId}/seats")
  public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeats(
      @Parameter(description = "회차 ID") @PathVariable Long showId) {
    return ResponseEntity.ok(ApiResponse.ok(seatService.getSeats(showId)));
  }

  @Operation(summary = "좌석 점유 요청", description = "선택한 좌석을 임시 점유합니다. Kafka를 통해 비동기로 처리됩니다.")
  @PostMapping("/{showId}/seats/hold")
  public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeats(
      @Parameter(description = "회차 ID") @PathVariable Long showId,
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody SeatHoldRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(seatService.holdSeats(showId, userId, request)));
  }

  @Operation(summary = "좌석 점유 해제", description = "임시 점유 중인 좌석을 해제합니다.")
  @DeleteMapping("/reservations/{reservationId}/hold")
  public ResponseEntity<ApiResponse<Void>> releaseSeats(
      @Parameter(description = "예매 ID") @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    seatService.releaseSeats(reservationId, userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}