package com.stagepass.api.security;

import com.stagepass.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtProvider jwtProvider;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    String token = resolveToken(request);

    if (StringUtils.hasText(token)) {
      try {
        if (jwtProvider.validate(token)) {
          Long userId = jwtProvider.getUserId(token);
          String role = jwtProvider.getRole(token);

          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(
                  userId,
                  null,
                  List.of(new SimpleGrantedAuthority("ROLE_" + role))
              );
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (BusinessException e) {
        // @RestControllerAdvice는 필터 계층 예외를 처리하지 못하므로 여기서 직접 응답 작성
        log.warn("[JWT] token validation failed - {}", e.getMessage());
        sendErrorResponse(response, e.getErrorCode().getStatus().value(), e.getMessage());
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private void sendErrorResponse(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
    response.getWriter().write("{\"success\":false,\"message\":\"" + escaped + "\"}");
  }

  private String resolveToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    return null;
  }
}