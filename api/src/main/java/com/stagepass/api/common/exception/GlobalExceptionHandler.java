package com.stagepass.api.common.exception;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    log.warn("[Exception] {} - {}", e.getErrorCode(), e.getMessage());
    return ResponseEntity
        .status(e.getErrorCode().getStatus())
        .body(ApiResponse.fail(e.getMessage()));
  }

  // @Valid 검증 실패 — 필드별 오류 메시지를 하나의 문자열로 합쳐서 반환
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.joining(", "));
    log.warn("[Validation] {}", message);
    return ResponseEntity.badRequest().body(ApiResponse.fail(message));
  }

  // JSON 파싱 실패 (잘못된 타입, 누락된 필수 필드 등)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
    log.warn("[Request] JSON parse failed - {}", e.getMessage());
    return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid request format."));
  }

  // DB unique 제약 위반 (이메일 중복 등 비즈니스 검증을 통과한 경우의 최후 방어)
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
    log.warn("[DB] constraint violation - {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.fail("Data already exists."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
    log.warn("[Exception] access denied - {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.fail("접근 권한이 없습니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("[Exception] internal server error", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.fail("Internal server error."));
  }
}