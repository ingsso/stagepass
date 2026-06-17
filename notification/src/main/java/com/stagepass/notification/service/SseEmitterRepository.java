package com.stagepass.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Repository
public class SseEmitterRepository {

  // userId → 복수 SseEmitter (웹 + 모바일 동시 연결 지원)
  private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public void save(Long userId, SseEmitter emitter) {
    emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    log.debug("[SSE] emitter saved userId={} count={}", userId, emitters.get(userId).size());
  }

  public List<SseEmitter> findAllByUserId(Long userId) {
    return emitters.getOrDefault(userId, new CopyOnWriteArrayList<>());
  }

  // 특정 emitter만 제거 (타임아웃·에러 시)
  public void delete(Long userId, SseEmitter emitter) {
    List<SseEmitter> list = emitters.get(userId);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) emitters.remove(userId);
    }
    log.debug("[SSE] emitter removed userId={}", userId);
  }

  // 유저의 모든 연결 제거 (로그아웃 등)
  public void deleteAll(Long userId) {
    emitters.remove(userId);
    log.debug("[SSE] all emitters removed userId={}", userId);
  }

  public boolean exists(Long userId) {
    List<SseEmitter> list = emitters.get(userId);
    return list != null && !list.isEmpty();
  }
}