# StagePass — 공연 티켓 예매 시스템

> 티켓팅에서 가장 큰 문제인 **좌석 동시성**을 Redis로 해결하고,  
> **결제 안정성**은 Kafka Saga 패턴으로 확보한 이벤트 기반 백엔드 프로젝트

<br>

## 🚨 해결한 핵심 문제

### 1. 좌석 동시성 — 1000명이 동시에 같은 좌석을 선택하면?

**문제**  
DB 트랜잭션만으로는 수천 건의 동시 요청을 막을 수 없음 → 중복 예매 발생

**해결**  
Redis `SET NX PX` 명령으로 원자적 선점. TTL 5분 자동 해제.  
선점 해제 시 `GET + DEL` Lua 스크립트로 race condition 방지

**결과**  
1,000명 동시 요청 → **정확히 1명만 선점 성공** (k6 부하 테스트 검증)

```
Redis SET seat:{id}:holder NX PX 300000
→ 1명만 성공, 나머지 즉시 409 반환
```

---

### 2. 결제 안정성 — 외부 API 장애가 전체 서비스를 멈추면?

**문제**  
DB 트랜잭션 안에서 토스 API를 호출하면 응답 대기 중 커넥션 점유  
동시 100명 결제 시 커넥션 풀 고갈 → 서비스 전체 장애

**해결**  
Kafka Saga 패턴으로 결제를 비동기 분리.  
결제 실패 시 보상 트랜잭션으로 좌석 자동 해제.  
토스 API 장애 시 Circuit Breaker(resilience4j)로 빠른 실패 처리

**결과**  
결제 실패해도 좌석 선점 자동 해제, 중복 결제 0건 보장

```
사용자 결제 요청
→ Kafka payment.requested 발행 (DB 커밋, 커넥션 즉시 반납)
→ Payment 서비스가 독립적으로 Toss API 호출
→ 성공: 예매 확정 / 실패: 보상 트랜잭션 (선점 해제)
```

---

### 3. 트래픽 폭증 — 티켓 오픈 순간 수천 명이 동시 접속하면?

**문제**  
모든 사용자가 동시에 좌석 조회 + 선점 시도 → DB 부하 폭증

**해결**  
Redis 대기열 시스템으로 순번 관리 (Sorted Set, score = 진입 timestamp).  
좌석 목록은 `@Cacheable` + Redis Pipeline으로 TTL 10s 캐싱.  
N+1 쿼리 JOIN FETCH로 제거

**결과**  
좌석 목록 API p95 응답시간 **10ms 이하** 유지

<br>

## 🔧 개선 경험 (Before → After)

### 좌석 목록 N+1 문제

| | Before | After |
|---|---|---|
| 쿼리 수 | 좌석 N개 × 구역 조회 = N+1 | JOIN FETCH 단일 쿼리 |
| 캐싱 | 없음 | Redis @Cacheable TTL 10s |
| p95 응답 | 임계값 초과 | **10ms** |

### 결제 처리 구조

| | Before (가정) | After |
|---|---|---|
| 구조 | 단일 트랜잭션 + 토스 API 동기 호출 | Kafka 비동기 분리 |
| 문제 | 커넥션 점유, 실패 시 롤백 불가 | 커넥션 즉시 반납, 보상 트랜잭션 |
| 중복 결제 | 방어 어려움 | Idempotency Key로 0건 보장 |

### 취소 대기 순번 유실 문제

| | Before | After |
|---|---|---|
| 복구 방식 | `add()` → 현재 시각이 score → 맨 뒤로 밀림 | `addWithScore(originalScore)` → 원래 순번 유지 |
| 문제 | 알림 실패 시 1순위가 맨 뒤로 | 실패해도 순번 보존 |

<br>

## 💡 주니어가 잘 안 하는 것 (킬링 포인트)

| 기술 | 내용 |
|------|------|
| **Kafka Saga 패턴** | 결제 성공/실패를 이벤트로 분리, 보상 트랜잭션 구현 |
| **Redis ZSet 순번 관리** | 대기열/취소대기 모두 Sorted Set으로 선착순 보장 |
| **비관적 락 + 데드락 방지** | 자리 교환 시 작은 ID 먼저 락 획득으로 데드락 원천 차단 |
| **SSE + Redis Pub/Sub** | 다중 인스턴스 환경에서 실시간 알림 수평 확장 |
| **포이즌 필 처리** | Kafka 역직렬화 실패 메시지 무한 재시도 방지 |

<br>

## 🛠 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 21, Spring Boot 3.3, Gradle 멀티모듈 |
| Message Broker | Apache Kafka |
| Cache / 동시성 | Redis 7 (Sorted Set, Pub/Sub, Pipeline) |
| Database | PostgreSQL 16 |
| 결제 | 토스페이먼츠 + resilience4j Circuit Breaker |
| 실시간 | SSE + Redis Pub/Sub |
| 인증 | JWT (Access 1h + Refresh 7d) |

<br>

## 📊 부하 테스트 결과 (k6)

| 시나리오 | 결과 |
|----------|------|
| 좌석 목록 조회 (1000 VU) | p95 **10ms**, 에러율 0.01% 미만 |
| 동시 좌석 선점 (1000 VU, 동일 좌석) | **1명만 성공**, 나머지 409 |

<br>

## 🏗 시스템 구조

```
Client
  ├── REST → API 서버 (:8080)       # 좌석 선점, 예매, 대기열
  ├── REST → Admin 서버 (:8081)     # 공연/좌석 관리
  └── SSE  → Notification (:8083)   # 실시간 알림

Kafka
  ├── payment.requested → Payment 서비스 → Toss API
  ├── payment.completed → 예매 확정 + 대기열 활성화
  └── payment.failed    → 보상 트랜잭션 (선점 해제)

Redis
  ├── SET NX PX     → 좌석 선점 원자성
  ├── Sorted Set    → 대기열 / 취소대기 순번
  ├── Pub/Sub       → SSE 다중 인스턴스 브로드캐스트
  └── @Cacheable    → 좌석 목록 캐싱
```

<br>

## 🎤 면접 한 줄 요약

> "티켓팅에서 가장 큰 문제인 좌석 동시성을 Redis SET NX로 해결했고,  
> 결제는 Kafka Saga 패턴으로 분산 처리해 안정성을 확보했습니다.  
> 트래픽 폭증 대비 대기열 시스템도 Redis Sorted Set으로 직접 구현했습니다."

<br>

## 🚀 실행 방법

```bash
# 1. 인프라
docker-compose up -d

# 2. 설정
cp api/src/main/resources/application-local.yaml.example \
   api/src/main/resources/application-local.yaml
# JWT 시크릿, Toss API 키 입력

# 3. 실행
./gradlew :api:bootRun --args='--spring.profiles.active=local'
./gradlew :payment:bootRun --args='--spring.profiles.active=local'
./gradlew :notification:bootRun --args='--spring.profiles.active=local'

# 4. API 문서
# http://localhost:8080/swagger-ui.html
```
