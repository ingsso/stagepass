package com.stagepass.api.payment.controller;

import com.stagepass.api.payment.dto.*;
import com.stagepass.api.payment.service.ApiPaymentService;
import com.stagepass.common.response.ApiResponse;
import com.stagepass.payment.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final ApiPaymentService apiPaymentService;

  // 결제 준비 — orderId 발급
  @PostMapping("/init")
  public ResponseEntity<ApiResponse<PaymentInitResponse>> initPayment(
      @AuthenticationPrincipal Long userId,
      @RequestBody PaymentInitRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(apiPaymentService.initPayment(userId, request)));
  }

  // 토스 결제 승인 → Saga 시작
  @PostMapping("/confirm")
  public ResponseEntity<ApiResponse<Void>> confirmPayment(
      @AuthenticationPrincipal Long userId,
      @RequestBody PaymentRequest request) {
    apiPaymentService.confirmPayment(userId, request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  // 결제 내역 조회
  @GetMapping("/reservations/{reservationId}")
  public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
      @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(apiPaymentService.getPayment(reservationId, userId)));
  }
}