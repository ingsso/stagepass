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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final UserRepository userRepository;
  private final AdminJwtProvider adminJwtProvider;
  private final PasswordEncoder passwordEncoder;

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