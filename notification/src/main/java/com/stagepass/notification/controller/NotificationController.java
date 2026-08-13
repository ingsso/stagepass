package com.stagepass.notification.controller;

import com.stagepass.notification.service.SseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// TODO: JwtProvider를 common 모듈로 이동 후 JWT 기반 userId 검증으로 교체
//       현재는 포트 8083을 내부망에서만 접근 가능하도록 인프라 레벨에서 제한 필요
@Tag(name = "알림", description = "SSE 기반 실시간 알림 구독 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final SseNotificationService sseNotificationService;

  @Operation(summary = "알림 구독", description = "SSE(Server-Sent Events)로 실시간 알림을 구독합니다.")
  @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @Parameter(description = "사용자 ID") @RequestParam Long userId) {
    return sseNotificationService.subscribe(userId);
  }
}