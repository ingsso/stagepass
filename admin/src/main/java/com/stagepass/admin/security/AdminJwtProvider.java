package com.stagepass.admin.security;

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
public class AdminJwtProvider {

  private final SecretKey secretKey;
  private final long expiration;

  public AdminJwtProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration}") long expiration) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expiration = expiration;
  }

  public String createToken(Long userId) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("role", "ADMIN")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(secretKey)
        .compact();
  }

  public Long getUserId(String token) {
    return Long.parseLong(getClaims(token).getSubject());
  }

  public boolean validate(String token) {
    try {
      getClaims(token);
      return true;
    } catch (JwtException e) {
      log.warn("[AdminJwt] invalid token: {}", e.getMessage());
      return false;
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