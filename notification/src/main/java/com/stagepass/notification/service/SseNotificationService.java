package com.stagepass.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseNotificationService {

  private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분

  private final SseEmitterRepository emitterRepository;

  // SSE 구독 — 클라이언트가 연결 시 호출
  public SseEmitter subscribe(Long userId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

    emitter.onCompletion(() -> emitterRepository.delete(userId));
    emitter.onTimeout(() -> {
      emitterRepository.delete(userId);
      log.debug("[SSE] 타임아웃 userId={}", userId);
    });
    emitter.onError(e -> {
      emitterRepository.delete(userId);
      log.debug("[SSE] 에러 userId={} error={}", userId, e.getMessage());
    });

    emitterRepository.save(userId, emitter);

    // 연결 직후 더미 이벤트 — 503 방지
    sendToUser(userId, "CONNECTED", "SSE 연결 완료");

    return emitter;
  }

  // 특정 유저에게 알림 발송
  public void sendToUser(Long userId, String type, String message) {
    emitterRepository.findByUserId(userId).ifPresent(emitter -> {
      try {
        emitter.send(SseEmitter.event()
            .name(type)
            .data(message));
        log.debug("[SSE] 발송 완료 userId={} type={}", userId, type);
      } catch (IOException e) {
        log.warn("[SSE] 발송 실패 userId={} type={} — 연결 제거", userId, type);
        emitterRepository.delete(userId);
        emitter.completeWithError(e);
      }
    });
  }
}