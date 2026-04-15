package com.stagepass.admin.security;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.common.response.ApiResponse;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 인증", description = "어드민 로그인 및 JWT 발급 API")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final UserRepository userRepository;
  private final AdminJwtProvider adminJwtProvider;
  private final PasswordEncoder passwordEncoder;

  @Operation(summary = "어드민 로그인", description = "어드민 계정으로 로그인하여 JWT 토큰을 발급받습니다. ADMIN 권한이 없는 계정은 거부됩니다.")
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AdminTokenResponse>> login(
      @RequestBody AdminLoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    if (user.getRole() != UserRole.ADMIN) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_PASSWORD);
    }

    String token = adminJwtProvider.createToken(user.getId());
    return ResponseEntity.ok(ApiResponse.ok(new AdminTokenResponse(token)));
  }

  @Getter
  @NoArgsConstructor
  static class AdminLoginRequest {
    private String email;
    private String password;
  }

  @Getter
  @lombok.AllArgsConstructor
  static class AdminTokenResponse {
    private String accessToken;
  }
}