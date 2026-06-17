package com.stagepass.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseNotificationService {

  private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분

  private final SseEmitterRepository emitterRepository;

  // SSE 구독 — 클라이언트가 연결 시 호출 (탭/기기별 복수 연결 허용)
  public SseEmitter subscribe(Long userId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

    emitter.onCompletion(() -> emitterRepository.delete(userId, emitter));
    emitter.onTimeout(() -> {
      emitterRepository.delete(userId, emitter);
      log.debug("[SSE] timeout userId={}", userId);
    });
    emitter.onError(e -> {
      emitterRepository.delete(userId, emitter);
      log.debug("[SSE] error userId={} error={}", userId, e.getMessage());
    });

    emitterRepository.save(userId, emitter);

    // 연결 직후 더미 이벤트 — 503 방지
    sendToUser(userId, "CONNECTED", "SSE connected");

    return emitter;
  }

  // 특정 유저의 모든 연결에 알림 발송
  public void sendToUser(Long userId, String type, String message) {
    List<SseEmitter> targets = emitterRepository.findAllByUserId(userId);
    for (SseEmitter emitter : targets) {
      try {
        emitter.send(SseEmitter.event().name(type).data(message));
        log.debug("[SSE] sent userId={} type={}", userId, type);
      } catch (IOException e) {
        log.warn("[SSE] send failed userId={} type={} - removing emitter", userId, type);
        emitterRepository.delete(userId, emitter);
        emitter.completeWithError(e);
      }
    }
  }
}