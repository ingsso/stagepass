package com.stagepass.api.auth.service;

import com.stagepass.api.auth.dto.LoginRequest;
import com.stagepass.api.auth.dto.SignUpRequest;
import com.stagepass.api.auth.dto.TokenResponse;
import com.stagepass.api.security.JwtProvider;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.RefreshTokenRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks
  private AuthService authService;

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRedisRepository refreshTokenRepository;
  @Mock private JwtProvider jwtProvider;
  @Mock private PasswordEncoder passwordEncoder;

  // ──────────────────────────────────────────────
  // signUp
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("회원가입 성공")
  void signUp_성공() {
    // given
    SignUpRequest request = new SignUpRequest();
    ReflectionTestUtils.setField(request, "email", "test@test.com");
    ReflectionTestUtils.setField(request, "password", "password123");
    ReflectionTestUtils.setField(request, "name", "홍길동");
    ReflectionTestUtils.setField(request, "phone", "010-1234-5678");
    given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
    given(passwordEncoder.encode(anyString())).willReturn("encoded_password");

    // when
    authService.signUp(request);

    // then
    then(userRepository).should().save(any(User.class));
  }

  @Test
  @DisplayName("회원가입 실패 - 중복 이메일")
  void signUp_중복이메일_예외() {
    // given
    SignUpRequest request = new SignUpRequest();
    ReflectionTestUtils.setField(request, "email", "dup@test.com");
    ReflectionTestUtils.setField(request, "password", "password123");
    given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> authService.signUp(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

    then(userRepository).should(never()).save(any());
  }

  // ──────────────────────────────────────────────
  // login
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("로그인 성공 - 토큰 반환")
  void login_성공() {
    // given
    LoginRequest request = new LoginRequest();
    ReflectionTestUtils.setField(request, "email", "test@test.com");
    ReflectionTestUtils.setField(request, "password", "password123");
    User user = User.builder()
        .email("test@test.com")
        .passwordHash("encoded_password")
        .name("홍길동")
        .role(UserRole.USER)
        .build();

    given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));
    given(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).willReturn(true);
    given(jwtProvider.createAccessToken(any(), anyString())).willReturn("access_token");
    given(jwtProvider.createRefreshToken(any())).willReturn("refresh_token");

    // when
    TokenResponse response = authService.login(request);

    // then
    assertThat(response.getAccessToken()).isEqualTo("access_token");
    assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
    then(refreshTokenRepository).should().save(any(), anyString());
  }

  @Test
  @DisplayName("로그인 실패 - 존재하지 않는 유저")
  void login_유저없음_예외() {
    // given
    LoginRequest request = new LoginRequest();
    ReflectionTestUtils.setField(request, "email", "none@test.com");
    ReflectionTestUtils.setField(request, "password", "password123");
    given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("로그인 실패 - 비밀번호 불일치")
  void login_비밀번호불일치_예외() {
    // given
    LoginRequest request = new LoginRequest();
    ReflectionTestUtils.setField(request, "email", "test@test.com");
    ReflectionTestUtils.setField(request, "password", "wrong_password");
    User user = User.builder()
        .email("test@test.com")
        .passwordHash("encoded_password")
        .name("홍길동")
        .role(UserRole.USER)
        .build();

    given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(user));
    given(passwordEncoder.matches(request.getPassword(), user.getPasswordHash())).willReturn(false);

    // when & then
    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_PASSWORD);
  }

  // ──────────────────────────────────────────────
  // reissue
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("토큰 재발급 성공")
  void reissue_성공() {
    // given
    String refreshToken = "valid_refresh_token";
    Long userId = 1L;
    User user = User.builder()
        .email("test@test.com")
        .passwordHash("encoded")
        .name("홍길동")
        .role(UserRole.USER)
        .build();

    given(jwtProvider.validate(refreshToken)).willReturn(true);
    given(jwtProvider.getUserId(refreshToken)).willReturn(userId);
    given(refreshTokenRepository.get(userId)).willReturn(refreshToken);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(jwtProvider.createAccessToken(any(), anyString())).willReturn("new_access_token");
    given(jwtProvider.createRefreshToken(any())).willReturn("new_refresh_token");

    // when
    TokenResponse response = authService.reissue(refreshToken);

    // then
    assertThat(response.getAccessToken()).isEqualTo("new_access_token");
  }

  @Test
  @DisplayName("토큰 재발급 실패 - Redis 저장 토큰과 불일치")
  void reissue_토큰불일치_예외() {
    // given
    String refreshToken = "request_token";
    Long userId = 1L;

    given(jwtProvider.validate(refreshToken)).willReturn(true);
    given(jwtProvider.getUserId(refreshToken)).willReturn(userId);
    given(refreshTokenRepository.get(userId)).willReturn("stored_different_token");

    // when & then
    assertThatThrownBy(() -> authService.reissue(refreshToken))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  // ──────────────────────────────────────────────
  // logout
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("로그아웃 성공 - Redis 리프레시 토큰 삭제")
  void logout_성공() {
    // given
    Long userId = 1L;

    // when
    authService.logout(userId);

    // then
    then(refreshTokenRepository).should().delete(userId);
  }
}
