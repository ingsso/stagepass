package com.stagepass.api.payment.controller;

import jakarta.validation.Valid;
import com.stagepass.api.payment.dto.*;
import jakarta.validation.Valid;
import com.stagepass.api.payment.service.ApiPaymentService;
import jakarta.validation.Valid;
import com.stagepass.common.response.ApiResponse;
import jakarta.validation.Valid;
import com.stagepass.api.payment.dto.PaymentConfirmRequest;
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

@Tag(name = "결제", description = "Toss Payments 연동 결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final ApiPaymentService apiPaymentService;

  @Operation(summary = "결제 초기화", description = "결제 준비 단계로 orderId를 발급합니다. 토스 결제창 호출 전에 먼저 호출해야 합니다.")
  @PostMapping("/init")
  public ResponseEntity<ApiResponse<PaymentInitResponse>> initPayment(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody PaymentInitRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(apiPaymentService.initPayment(userId, request)));
  }

  @Operation(summary = "결제 승인", description = "토스 결제창에서 결제 완료 후 최종 승인을 요청합니다. Kafka를 통해 Saga 패턴으로 처리됩니다.")
  @PostMapping("/confirm")
  public ResponseEntity<ApiResponse<Void>> confirmPayment(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody PaymentConfirmRequest request) {
    apiPaymentService.confirmPayment(userId, request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(summary = "결제 내역 조회", description = "특정 예매의 결제 내역을 조회합니다.")
  @GetMapping("/reservations/{reservationId}")
  public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
      @Parameter(description = "예매 ID") @PathVariable Long reservationId,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.ok(apiPaymentService.getPayment(reservationId, userId)));
  }
}