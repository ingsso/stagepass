package com.stagepass.notification.controller;

import com.stagepass.notification.service.SseNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "알림", description = "SSE 기반 실시간 알림 구독 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final SseNotificationService sseNotificationService;

  @Operation(summary = "알림 구독", description = "SSE(Server-Sent Events)로 실시간 알림을 구독합니다. 결제 완료, 예매 확정 등의 이벤트를 수신합니다.")
  @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @Parameter(description = "사용자 ID") @RequestParam Long userId) {
    return sseNotificationService.subscribe(userId);
  }
}