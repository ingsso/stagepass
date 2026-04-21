package com.stagepass.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class SseEmitterRepository {

  // userId → SseEmitter
  private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

  public void save(Long userId, SseEmitter emitter) {
    emitters.put(userId, emitter);
    log.debug("[SSE] 연결 저장 userId={} 현재 연결 수={}", userId, emitters.size());
  }

  public Optional<SseEmitter> findByUserId(Long userId) {
    return Optional.ofNullable(emitters.get(userId));
  }

  public void delete(Long userId) {
    emitters.remove(userId);
    log.debug("[SSE] 연결 제거 userId={} 현재 연결 수={}", userId, emitters.size());
  }

  public boolean exists(Long userId) {
    return emitters.containsKey(userId);
  }
}