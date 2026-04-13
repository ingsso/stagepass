package com.stagepass.notification.controller;

import com.stagepass.notification.service.SseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final SseNotificationService sseNotificationService;

  // GET /api/notifications/subscribe?userId=1
  // 실제로는 JWT에서 userId 추출 — api 모듈에서 구현
  @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(@RequestParam Long userId) {
    return sseNotificationService.subscribe(userId);
  }
}