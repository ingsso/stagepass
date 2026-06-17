package com.stagepass.admin.security;

import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AdminGlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    log.warn("[AdminException] {} - {}", e.getErrorCode(), e.getMessage());
    return ResponseEntity
        .status(e.getErrorCode().getStatus())
        .body(ApiResponse.fail(e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("[AdminException] internal server error", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.fail("Internal server error."));
  }
}