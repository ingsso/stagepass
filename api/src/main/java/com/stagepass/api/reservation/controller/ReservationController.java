package com.stagepass.api.reservation.controller;

import com.stagepass.api.reservation.dto.ReservationResponse;
import com.stagepass.api.reservation.service.ReservationService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(reservationService.getMyReservations(userId)));
  }

  @GetMapping("/{reservationId}")
  public ResponseEntity<ApiResponse<ReservationResponse>> getOne(
      @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(reservationService.getOne(reservationId, userId)));
  }

  @DeleteMapping("/{reservationId}")
  public ResponseEntity<ApiResponse<Void>> cancel(
      @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    reservationService.cancel(reservationId, userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}