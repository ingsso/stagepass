package com.stagepass.api.seat.controller;

import com.stagepass.api.seat.dto.*;
import com.stagepass.api.seat.service.SeatService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class SeatController {

  private final SeatService seatService;

  @GetMapping("/{showId}/seats")
  public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeats(@PathVariable Long showId) {
    return ResponseEntity.ok(ApiResponse.ok(seatService.getSeats(showId)));
  }

  @PostMapping("/{showId}/seats/hold")
  public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeats(
      @PathVariable Long showId,
      @AuthenticationPrincipal Long userId,
      @RequestBody SeatHoldRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(seatService.holdSeats(showId, userId, request)));
  }

  @DeleteMapping("/reservations/{reservationId}/hold")
  public ResponseEntity<ApiResponse<Void>> releaseSeats(
      @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    seatService.releaseSeats(reservationId, userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}