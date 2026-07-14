package com.stagepass.api.queue.service;

import com.stagepass.api.queue.dto.QueueEnterResponse;
import com.stagepass.api.queue.dto.QueueStatusResponse;
import com.stagepass.common.exception.BusinessException;
import com.stagepass.common.exception.ErrorCode;
import com.stagepass.domain.performance.ShowRepository;
import com.stagepass.domain.queue.QueueEntry;
import com.stagepass.domain.queue.QueueEntryRepository;
import com.stagepass.domain.queue.QueueStatus;
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
  private final QueueEntryWriter queueEntryWriter;

  // 대기열 진입
  @Transactional
  public QueueEnterResponse enter(Long showId, Long userId) {
    showRepository.findById(showId)
        .orElseThrow(() -> new BusinessException(ErrorCode.SHOW_NOT_FOUND));
    userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // Redis에 이미 있으면 현재 순번 반환 — DB 불일치·미활성화 상태 복구 포함
    Long existingRank = queueRedisRepository.getRank(showId, userId);
    if (existingRank != null) {
      Long total = queueRedisRepository.getSize(showId);
      QueueEntry existingRedisEntry = queueEntryRepository.findByShowIdAndUserId(showId, userId).orElse(null);

      // Redis에는 있는데 DB에 없는 orphan 상태 → DB 진입 레코드 복구
      if (existingRedisEntry == null) {
        queueEntryWriter.tryInsert(showId, userId); // 이미 있으면 no-op
        existingRedisEntry = queueEntryRepository.findByShowIdAndUserId(showId, userId).orElse(null);
      }

      boolean alreadyActivated = existingRedisEntry != null && existingRedisEntry.getStatus() == QueueStatus.ACTIVATED;
      if (!alreadyActivated && existingRedisEntry != null && existingRank <= ACTIVATE_BATCH_SIZE) {
        existingRedisEntry.activate();
        eventPublisher.publishQueueActivated(new QueueEvent(showId, userId, existingRank));
        alreadyActivated = true;
      }
      return new QueueEnterResponse(existingRank, total, alreadyActivated);
    }

    // DB에 이미 있으면 Redis 재등록 (Redis flush 등으로 인한 DB-Redis 불일치 복구)
    QueueEntry existingEntry = queueEntryRepository.findByShowIdAndUserId(showId, userId).orElse(null);
    if (existingEntry != null) {
      queueRedisRepository.enter(showId, userId);
      Long rank = queueRedisRepository.getRank(showId, userId);
      Long total = queueRedisRepository.getSize(showId);
      boolean alreadyActivated = existingEntry.getStatus() == QueueStatus.ACTIVATED;
      // 재진입 시 rank ≤ 10이고 아직 미활성화면 즉시 활성화
      if (!alreadyActivated && rank != null && rank <= ACTIVATE_BATCH_SIZE) {
        existingEntry.activate();
        eventPublisher.publishQueueActivated(new QueueEvent(showId, userId, rank));
        alreadyActivated = true;
      }
      return new QueueEnterResponse(rank != null ? rank : 0L, total != null ? total : 0L, alreadyActivated);
    }

    // 동시 진입 경쟁 처리: ON CONFLICT DO NOTHING INSERT — 이미 있으면 재진입으로 처리
    if (!queueEntryWriter.tryInsert(showId, userId)) {
      // 다른 요청이 먼저 INSERT를 커밋한 케이스 — 현재 상태 그대로 반환
      queueRedisRepository.enter(showId, userId); // 멱등: 이미 있으면 score만 갱신
      Long raceRank = queueRedisRepository.getRank(showId, userId);
      Long raceTotal = queueRedisRepository.getSize(showId);
      QueueEntry raceEntry = queueEntryRepository.findByShowIdAndUserId(showId, userId).orElse(null);
      boolean raceActivated = raceEntry != null && raceEntry.getStatus() == QueueStatus.ACTIVATED;
      return new QueueEnterResponse(raceRank != null ? raceRank : 0L, raceTotal != null ? raceTotal : 0L, raceActivated);
    }

    // DB 저장 성공 후 Redis 진입
    queueRedisRepository.enter(showId, userId);
    Long rank = queueRedisRepository.getRank(showId, userId);
    Long total = queueRedisRepository.getSize(showId);

    // Kafka 진입 이벤트 발행
    eventPublisher.publishQueueEntered(new QueueEvent(showId, userId, rank));

    // 즉시 입장 가능 여부 (순번 10 이내)
    boolean activated = rank != null && rank <= ACTIVATE_BATCH_SIZE;
    if (activated) {
      activateUser(showId, userId, rank);
    }

    log.info("[Queue] entered showId={} userId={} rank={}", showId, userId, rank);
    return new QueueEnterResponse(rank != null ? rank : 0L, total != null ? total : 0L, activated);
  }

  // 대기열 순번 조회 — rank ≤ 10이고 WAITING이면 즉시 활성화 (TX 실패로 인한 stuck 복구)
  @Transactional
  public QueueStatusResponse getStatus(Long showId, Long userId) {
    Long rank = queueRedisRepository.getRank(showId, userId);
    Long total = queueRedisRepository.getSize(showId);

    if (rank == null) {
      return new QueueStatusResponse(null, total, null, "NOT_IN_QUEUE");
    }

    QueueEntry entry = queueEntryRepository.findByShowIdAndUserId(showId, userId).orElse(null);

    // orphan Redis 상태(Redis에는 있는데 DB에 없음) 복구
    if (entry == null) {
      queueEntryWriter.tryInsert(showId, userId);
      entry = queueEntryRepository.findByShowIdAndUserId(showId, userId).orElse(null);
    }

    boolean activated = entry != null && entry.getStatus() == QueueStatus.ACTIVATED;

    // 진입 TX 실패로 rank ≤ 10임에도 WAITING 상태면 여기서 복구
    if (!activated && entry != null && rank <= ACTIVATE_BATCH_SIZE) {
      entry.activate();
      eventPublisher.publishQueueActivated(new QueueEvent(showId, userId, rank));
      activated = true;
    }

    String status = activated ? "ACTIVATED" : "WAITING";
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

    log.info("[Queue] next batch activated showId={} count={}", showId, top.size());
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
    log.info("[Queue] left showId={} userId={}", showId, userId);
  }

  private void activateUser(Long showId, Long userId, Long rank) {
    queueEntryRepository.findByShowIdAndUserId(showId, userId)
        .ifPresentOrElse(
            entry -> {
              entry.activate();
              eventPublisher.publishQueueActivated(new QueueEvent(showId, userId, rank));
              log.info("[Queue] activated showId={} userId={}", showId, userId);
            },
            () -> log.warn("[Queue] activation skipped - DB entry not found showId={} userId={}", showId, userId)
        );
  }
}