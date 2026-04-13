package com.stagepass.api.auth.controller;

import com.stagepass.api.auth.dto.*;
import com.stagepass.api.auth.service.AuthService;
import com.stagepass.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<Void>> signUp(@RequestBody SignUpRequest request) {
    authService.signUp(request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
  }

  @PostMapping("/reissue")
  public ResponseEntity<ApiResponse<TokenResponse>> reissue(@RequestHeader("Refresh-Token") String refreshToken) {
    return ResponseEntity.ok(ApiResponse.ok(authService.reissue(refreshToken)));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Long userId) {
    authService.logout(userId);
    return ResponseEntity.ok(ApiResponse.ok());
  }
}