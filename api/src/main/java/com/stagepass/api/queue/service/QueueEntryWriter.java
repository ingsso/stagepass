package com.stagepass.api.queue.service;

import com.stagepass.domain.queue.QueueEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class QueueEntryWriter {

    private final QueueEntryRepository queueEntryRepository;

    /**
     * 진입 레코드를 INSERT 하고, 실제로 삽입했으면 true 를 반환한다.
     *
     * 호출자(QueueService.enter)의 트랜잭션에 합류한다. REQUIRES_NEW 를 쓰면 안 된다.
     * 외부 트랜잭션이 커넥션을 쥔 채 두 번째 커넥션을 요구하게 되어,
     * 동시 요청이 풀 크기에 도달하는 순간 모든 스레드가 서로의 커넥션 반납을 기다리는
     * 자기 교착에 빠진다(HikariCP 기본 풀 10 → 1,000 VU 부하에서 전면 정지 확인).
     *
     * insertIfAbsent 는 ON CONFLICT DO NOTHING 이라 중복 시 예외 없이 0 을 반환하므로,
     * 애초에 외부 트랜잭션이 오염되지 않는다. 즉 트랜잭션을 분리할 이유가 없다.
     */
    @Transactional
    boolean tryInsert(Long showId, Long userId) {
        return queueEntryRepository.insertIfAbsent(showId, userId) > 0;
    }
}
