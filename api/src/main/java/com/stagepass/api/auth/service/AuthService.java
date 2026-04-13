package com.stagepass.api.auth.service;

import com.stagepass.api.auth.dto.*;
import com.stagepass.api.security.JwtProvider;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.domain.user.UserRole;
import com.stagepass.infra.redis.RefreshTokenRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRedisRepository refreshTokenRepository;
  private final JwtProvider jwtProvider;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public void signUp(SignUpRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }
    User user = User.builder()
        .email(request.getEmail())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .name(request.getName())
        .phone(request.getPhone())
        .role(UserRole.USER)
        .build();
    userRepository.save(user);
  }

  @Transactional
  public TokenResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_PASSWORD);
    }

    return issueTokens(user);
  }

  @Transactional
  public TokenResponse reissue(String refreshToken) {
    jwtProvider.validate(refreshToken);
    Long userId = jwtProvider.getUserId(refreshToken);

    String stored = refreshTokenRepository.get(userId);
    if (!refreshToken.equals(stored)) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    return issueTokens(user);
  }

  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.delete(userId);
  }

  private TokenResponse issueTokens(User user) {
    String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole().name());
    String refreshToken = jwtProvider.createRefreshToken(user.getId());
    refreshTokenRepository.save(user.getId(), refreshToken);
    return new TokenResponse(accessToken, refreshToken);
  }
}