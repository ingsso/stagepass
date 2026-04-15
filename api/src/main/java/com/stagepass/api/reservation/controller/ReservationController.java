package com.stagepass.api.reservation.controller;

import com.stagepass.api.reservation.dto.ReservationResponse;
import com.stagepass.api.reservation.service.ReservationService;
import com.stagepass.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "예매", description = "예매 조회 및 취소 API")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  @Operation(summary = "내 예매 목록 조회", description = "로그인한 사용자의 전체 예매 내역을 조회합니다.")
  @GetMapping
  public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(reservationService.getMyReservations(userId)));
  }

  @Operation(summary = "예매 상세 조회", description = "특정 예매의 상세 정보를 조회합니다.")
  @GetMapping("/{reservationId}")
  public ResponseEntity<ApiResponse<ReservationResponse>> getOne(
      @Parameter(description = "예매 ID") @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(reservationService.getOne(reservationId, userId)));
  }

  @Operation(summary = "예매 취소", description = "예매를 취소합니다. 결제 완료 상태인 경우 환불이 함께 처리됩니다.")
  @DeleteMapping("/{reservationId}")
  public ResponseEntity<ApiResponse<Void>> cancel(
      @Parameter(description = "예매 ID") @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    reservationService.cancel(reservationId, userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}