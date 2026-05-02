package com.stagepass.api.queue.service;

import com.stagepass.api.queue.dto.QueueEnterResponse;
import com.stagepass.api.queue.dto.QueueStatusResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.Show;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
import com.stagepass.domain.user.User;
import com.stagepass.domain.user.UserRepository;
import com.stagepass.infra.redis.QueueRedisRepository;
import com.stagepass.kafka.event.QueueEvent;
import com.stagepass.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

  private static final int ACTIVATE_BATCH_SIZE = 10; // 한 번에 입장 허가할 인원
  private static final long SECONDS_PER_PERSON = 30L; // 1인당 예상 처리 시간

  private final QueueRedisRepository queueRedisRepository;
  private final QueueEntryRepository queueEntryRepository;
  private final ShowRepository showRepository;
  private final UserRepository userRepository;
  private final EventPublisher eventPublisher;

  // 대기열 진입
  @Transactional
  public QueueEnterResponse enter(Long showId, Long userId) {
    showRepository.findById(showId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOW_NOT_FOUND));
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 이미 대기열에 있으면 현재 순번 반환
    Long existingRank = queueRedisRepository.getRank(showId, userId);
    if (existingRank != null) {
      Long total = queueRedisRepository.getSize(showId);
      return new QueueEnterResponse(existingRank, total, false);
    }

    // Redis Sorted Set 진입
    queueRedisRepository.enter(showId, userId);
    Long rank = queueRedisRepository.getRank(showId, userId);
    Long total = queueRedisRepository.getSize(showId);

    // DB 기록
    queueEntryRepository.save(
        QueueEntry.builder()
            .show(showRepository.getReferenceById(showId))
            .user(user)
            .rank(rank != null ? rank.intValue() : 0)
            .build()
    );

    // Kafka 진입 이벤트 발행
    eventPublisher.publishQueueEntered(new QueueEvent(showId, userId, rank));

    // 즉시 입장 가능 여부 (순번 10 이내)
    boolean activated = rank != null && rank <= ACTIVATE_BATCH_SIZE;
    if (activated) {
      activateUser(showId, userId, rank);
    }

    log.info("[Queue] 대기열 진입 showId={} userId={} rank={}", showId, userId, rank);
    return new QueueEnterResponse(rank != null ? rank : 0L, total != null ? total : 0L, activated);
  }

  // 대기열 순번 조회
  @Transactional(readOnly = true)
  public QueueStatusResponse getStatus(Long showId, Long userId) {
    Long rank = queueRedisRepository.getRank(showId, userId);
    Long total = queueRedisRepository.getSize(showId);

    if (rank == null) {
      return new QueueStatusResponse(null, total, null, "NOT_IN_QUEUE");
    }

    // DB에서 활성화 여부 확인
    QueueEntry entry = queueEntryRepository.findByShowIdAndUserId(showId, userId)
        .orElse(null);
    String status = (entry != null && entry.getStatus() == QueueStatus.ACTIVATED)
        ? "ACTIVATED" : "WAITING";

    long estimatedWait = (rank - 1) * SECONDS_PER_PERSON;

    return new QueueStatusResponse(rank, total, estimatedWait, status);
  }

  // 결제 완료 후 다음 배치 입장 허가 (PaymentService 에서 호출)
  // ZSet range 결과는 score 오름차순으로 순서가 보장되므로 getRank() 재조회 불필요
  @Transactional
  public void activateNextBatch(Long showId) {
    Set<String> top = queueRedisRepository.getTop(showId, ACTIVATE_BATCH_SIZE);
    if (top == null || top.isEmpty()) return;

    List<Long> userIds = top.stream().map(Long::parseLong).toList();

    // N+1 방지 — IN 절로 한 번에 조회
    Map<Long, QueueEntry> entryMap = queueEntryRepository.findByShowIdAndUserIdIn(showId, userIds)
        .stream()
        .collect(Collectors.toMap(e -> e.getUser().getId(), Function.identity()));

    long rank = 1L;
    for (Long userId : userIds) {
      QueueEntry entry = entryMap.get(userId);
      if (entry != null && entry.getStatus() == QueueStatus.ACTIVATED) {
        rank++;
        continue; // 이미 활성화됨 — 중복 이벤트 방지
      }
      if (entry != null) entry.activate();
      eventPublisher.publishQueueActivated(new QueueEvent(showId, userId, rank++));
    }

    log.info("[Queue] 다음 배치 입장 허가 showId={} count={}", showId, top.size());
  }

  // 대기열 퇴장 (입장 후 or 취소)
  @Transactional
  public void leave(Long showId, Long userId) {
    queueRedisRepository.remove(showId, userId);
    queueEntryRepository.findByShowIdAndUserId(showId, userId)
        .ifPresent(entry -> {
          // ACTIVATED 상태면 다음 배치 활성화
          if (entry.getStatus() == QueueStatus.ACTIVATED) {
            activateNextBatch(showId);
          }
        });
    log.info("[Queue] 대기열 퇴장 showId={} userId={}", showId, userId);
  }

  private void activateUser(Long showId, Long userId, Long rank) {
    queueEntryRepository.findByShowIdAndUserId(showId, userId)
        .ifPresentOrElse(
            entry -> {
              entry.activate();
              eventPublisher.publishQueueActivated(new QueueEvent(showId, userId, rank));
              log.info("[Queue] 입장 허가 showId={} userId={}", showId, userId);
            },
            () -> log.warn("[Queue] 입장 허가 스킵 — DB 엔트리 없음 showId={} userId={}", showId, userId)
        );
  }
}