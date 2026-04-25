package com.stagepass.api.common.exception;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
    log.warn("[Request] JSON 파싱 실패 - {}", e.getMessage());
    return ResponseEntity.badRequest().body(ApiResponse.fail("요청 형식이 올바르지 않습니다."));
  }

  // DB unique 제약 위반 (이메일 중복 등 비즈니스 검증을 통과한 경우의 최후 방어)
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
    log.warn("[DB] 제약 조건 위반 - {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.fail("이미 존재하는 데이터입니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("[Exception] 서버 오류", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.fail("서버 오류가 발생했습니다."));
  }
}