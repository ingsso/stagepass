package com.stagepass.api.security;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

  private final SecretKey secretKey;
  private final long accessExpiration;
  private final long refreshExpiration;

  public JwtProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration}") long accessExpiration,
      @Value("${jwt.refresh-expiration}") long refreshExpiration) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpiration = accessExpiration;
    this.refreshExpiration = refreshExpiration;
  }

  public String createAccessToken(Long userId, String role) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("role", role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessExpiration))
        .signWith(secretKey)
        .compact();
  }

  public String createRefreshToken(Long userId) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
        .signWith(secretKey)
        .compact();
  }

  public Long getUserId(String token) {
    return Long.parseLong(getClaims(token).getSubject());
  }

  public String getRole(String token) {
    return getClaims(token).get("role", String.class);
  }

  public boolean validate(String token) {
    try {
      getClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
    } catch (JwtException e) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }
  }

  private Claims getClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}