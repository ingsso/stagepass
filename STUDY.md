# stagepass 프로젝트 핵심 개념 정리

## 1. 트랜잭션 전파 레벨 (REQUIRED vs REQUIRES_NEW)

### 개념
- `REQUIRED` (기본값): 이미 트랜잭션이 있으면 **그 트랜잭션에 참여**, 없으면 새로 만듦
- `REQUIRES_NEW`: 이미 트랜잭션이 있든 없든 **무조건 새 트랜잭션을 만듦** (기존 트랜잭션은 잠깐 중단)

| | REQUIRED | REQUIRES_NEW |
|---|---|---|
| 기존 트랜잭션 있을 때 | 참여 | 중단하고 새로 시작 |
| 실패 시 | 전체 롤백 | 본인 것만 롤백 |

### 이 프로젝트에서 쓴 이유
`WaitlistService.notifyNext()`에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 적용

`ReservationService.cancel()` 안에서 `notifyNext()`를 호출함.
만약 `REQUIRED`를 썼다면 `notifyNext()`가 실패했을 때 예매 취소까지 롤백됨.
예매 취소는 성공했는데 알림 실패 때문에 취소가 없던 일이 되면 안 되므로 독립 트랜잭션으로 분리.

```java
// WaitlistService.java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void notifyNext(Long showId) { ... }
```

---

## 2. 비관적 락 + 데드락 방지

### 데드락이란
두 스레드가 서로 상대방이 가진 락을 기다리면서 둘 다 영원히 진행 못 하는 상태.

### 발생 시나리오
예매 A(id=1), 예매 B(id=2)가 있을 때 두 사용자가 동시에 교환 수락을 누르면:
- 사용자1: A 락 잡음 → B 락 기다림
- 사용자2: B 락 잡음 → A 락 기다림
- → 무한 대기 (데드락)

### 해결 방법: 항상 작은 ID 먼저 락 잡기
둘 다 같은 순서로 락을 잡으면 한 쪽이 기다리게 되고 데드락이 방지됨.

```java
// SeatExchangeService.java
Long id1 = Math.min(proposerReservationId, receiverReservationId);
Long id2 = Math.max(proposerReservationId, receiverReservationId);

Reservation res1 = reservationRepository.findByIdWithLock(id1); // 작은 ID 먼저
Reservation res2 = reservationRepository.findByIdWithLock(id2); // 큰 ID 나중
```

---

## 3. Redis ZSet + score 복구

### ZSet이란
Sorted Set. 각 값마다 **score(점수)**가 있고 score 기준으로 자동 정렬됨.
일반 Set은 순서가 없지만 ZSet은 순서가 있음.

### 이 프로젝트에서 쓴 방식
score로 `System.currentTimeMillis()` (등록 시각)를 저장 → 먼저 등록한 사람이 앞 순번.

```java
double score = System.currentTimeMillis();
redisTemplate.opsForZSet().add("waitlist:" + showId, userId, score);
```

### score 복구가 중요한 이유
`notifyNext()`에서 첫 번째 대기자를 꺼내서 알림 보내다 실패하면 Redis에 다시 넣어야 함.

이때 `add()`를 쓰면 score가 **현재 시각**으로 들어가서 맨 뒤로 밀림.
원래 1등이었던 사람의 순번을 잃게 되므로 **원래 score로 복구**해야 함.

```java
// 잘못된 방법 — 맨 뒤로 밀림
waitlistRedisRepository.add(showId, nextUserId);

// 올바른 방법 — 원래 순번 유지
waitlistRedisRepository.addWithScore(showId, nextUserId, originalScore);
```

---

## 4. Kafka 포이즌 필 (Poison Pill)

### 포이즌 필이란
Kafka 메시지가 깨져서 처리가 불가능한 상태. 재시도해도 똑같이 실패함.
이 메시지가 계속 살아있으면 Consumer가 무한 루프에 빠짐.

### 예외 분기 전략

| 예외 종류 | 원인 | 처리 |
|---|---|---|
| `JsonProcessingException` | 메시지 자체가 깨짐 | `ack.acknowledge()` — 재시도 없이 넘김 |
| `Exception` | DB 장애, 네트워크 등 | `throw` — 재시도하면 나중에 성공 가능 |

`JsonProcessingException`은 100번 재시도해도 결과가 안 바뀌므로 그냥 넘기는 것이 맞음.

```java
// PaymentFailureConsumer.java
} catch (JsonProcessingException e) {
    log.error("포이즌 필 message={}", message, e);
    ack.acknowledge(); // 재처리 없이 offset 커밋
} catch (Exception e) {
    throw new RuntimeException(e); // 재시도
}
```

---

## 5. Saga 패턴 + 보상 트랜잭션

### 왜 하나의 트랜잭션으로 안 하나
DB 트랜잭션 안에서 외부 API(토스)를 호출하면:
- 토스 API 응답 기다리는 동안 DB 커넥션이 계속 잡혀있음
- 동시 사용자가 많으면 커넥션 풀 고갈 → 서비스 전체 장애

### Saga 패턴으로 단계 분리
```
예매 확정 → payment.requested 발행 (DB 커밋, 커넥션 반납)
→ PaymentService가 토스 API 호출 (별도 서비스)
→ 성공: payment.completed 발행 → 예매 CONFIRMED
→ 실패: payment.failed 발행 → 보상 트랜잭션 실행
```

### 보상 트랜잭션이란
이미 커밋된 DB를 롤백할 수 없으니, **반대 작업으로 상태를 되돌리는 것**.
결제 실패 시 `PENDING` 예매를 `CANCELLED`로 바꾸는 게 보상 트랜잭션.

```
payment.failed 수신
→ 예매 상태를 CANCELLED로 변경
→ Redis 좌석 선점 해제
→ 대기자에게 알림
```

---

## 면접 예상 질문

1. `REQUIRES_NEW`를 왜 썼나요?
   - 알림 실패가 예매 취소를 롤백시키면 안 되기 때문에 독립 트랜잭션으로 분리

2. 비관적 락에서 왜 작은 ID 먼저 잡나요?
   - 항상 같은 순서로 락을 잡아야 데드락이 방지되기 때문

3. Redis ZSet에서 score를 왜 복구하나요?
   - `add()`는 현재 시각을 score로 써서 맨 뒤로 밀리므로 원래 순번을 잃지 않으려고

4. 포이즌 필을 왜 ack 처리하나요?
   - 메시지 자체가 깨진 거라 재시도해도 소용없고 무한 루프에 빠지기 때문

5. 왜 Saga 패턴을 썼나요?
   - 외부 API 호출을 트랜잭션 안에 두면 커넥션 풀 고갈 위험이 있기 때문
