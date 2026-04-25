package com.stagepass.api.auth.controller;

import jakarta.validation.Valid;
import com.stagepass.api.auth.dto.*;
import jakarta.validation.Valid;
import com.stagepass.api.auth.service.AuthService;
import jakarta.validation.Valid;
import com.stagepass.common.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "회원가입", description = "이메일과 비밀번호로 신규 회원을 등록합니다.")
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<Void>> signUp(@Valid @RequestBody SignUpRequest request) {
    authService.signUp(request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하여 JWT 액세스/리프레시 토큰을 발급받습니다.")
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
  }

  @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 새로운 액세스 토큰을 재발급합니다.")
  @PostMapping("/reissue")
  public ResponseEntity<ApiResponse<TokenResponse>> reissue(@RequestHeader("Refresh-Token") String refreshToken) {
    return ResponseEntity.ok(ApiResponse.ok(authService.reissue(refreshToken)));
  }

  @Operation(summary = "로그아웃", description = "현재 로그인된 사용자의 리프레시 토큰을 삭제합니다.")
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Long userId) {
    authService.logout(userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}