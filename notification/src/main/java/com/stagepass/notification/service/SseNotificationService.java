package com.stagepass.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
      } catch (Exception e) {
        // IOException(끊긴 연결) 외에 이미 에러난 AsyncContext 에서 IllegalStateException 도 올라옵니다.
        // 여기서 안 잡으면 죽은 emitter 하나 때문에 같은 유저의 나머지 연결이 전부 못 받습니다.
        log.warn("[SSE] send failed userId={} type={} - removing emitter", userId, type);
        emitterRepository.delete(userId, emitter);
        try {
          emitter.completeWithError(e);
        } catch (Exception ignored) {
          // 이미 완료·에러 처리된 emitter — 무시
        }
      }
    }
  }
}