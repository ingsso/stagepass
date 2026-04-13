package com.stagepass.api.common.exception;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("[Exception] 서버 오류", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.fail("서버 오류가 발생했습니다."));
  }
}