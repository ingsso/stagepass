# StagePass API 문서

화면(뷰) 기준으로 정리한 전체 API 목록입니다.

> **인증**: `🔒` 표시 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

---

## 사용자 앱 (port 8080)

### 1. 인증

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/auth/signup` | - | 회원가입 |
| POST | `/api/auth/login` | - | 로그인 (Access Token + Refresh Token 발급) |
| POST | `/api/auth/reissue` | - | Access Token 재발급 |
| POST | `/api/auth/logout` | 🔒 | 로그아웃 (Refresh Token 삭제) |

**POST /api/auth/signup**
```json
// Request
{
  "email": "user@example.com",
  "password": "password123",  // 8자 이상
  "name": "홍길동"
}
// Response 201
```

**POST /api/auth/login**
```json
// Request
{ "email": "user@example.com", "password": "password123" }

// Response 200
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**POST /api/auth/reissue**
```
// Request Header
Refresh-Token: eyJhbGciOiJIUzI1NiJ9...

// Response 200
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "refreshToken": "..." }
```

---

### 2. 공연 목록

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/performances` | - | 공연 목록 조회 (`?keyword=` 검색, 페이지네이션) |
| GET | `/api/performances/{performanceId}` | - | 공연 상세 조회 |
| GET | `/api/performances/{performanceId}/shows` | - | 회차 목록 조회 |

**GET /api/performances?keyword=뮤지컬&page=0&size=20**
```json
// Response 200
{
  "content": [
    {
      "id": 1,
      "title": "뮤지컬 레미제라블",
      "venue": "블루스퀘어",
      "startDate": "2025-06-01",
      "endDate": "2025-08-31",
      "thumbnailUrl": "https://..."
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

**GET /api/performances/{performanceId}/shows**
```json
// Response 200
[
  {
    "id": 10,
    "showDatetime": "2025-07-01T19:30:00",
    "totalSeats": 500,
    "remainingSeats": 123,
    "status": "ON_SALE"
  }
]
```

---

### 3. 대기열

> 예매 인기 공연은 대기열 진입 후 입장 허가를 받아야 좌석 선택 화면으로 이동합니다.
> 대기 순번은 **Redis Sorted Set**(score = 진입 timestamp)으로 관리되며, SSE + Redis Pub/Sub으로 실시간 전달됩니다.

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/shows/{showId}/queue` | 🔒 | 대기열 진입 |
| GET | `/api/shows/{showId}/queue/status` | 🔒 | 대기 순번 / 입장 허가 여부 조회 |
| DELETE | `/api/shows/{showId}/queue` | 🔒 | 대기열 이탈 |

**POST /api/shows/{showId}/queue**
```json
// Response 200
{
  "rank": 42,        // 현재 대기 순번 (1-based)
  "total": 300,      // 전체 대기 인원
  "status": "WAITING"
}
```

**GET /api/shows/{showId}/queue/status**
```json
// Response 200
{
  "rank": 5,
  "total": 120,
  "status": "WAITING"  // WAITING | ACTIVATED
}
```

---

### 4. 좌석 선택

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/shows/{showId}/seats` | - | 좌석 목록 조회 (Redis `@Cacheable` TTL 10s, Pipeline TTL 일괄 조회) |
| POST | `/api/shows/{showId}/seats/hold` | 🔒 | 좌석 임시 선점 (Redis SET NX, 5분 TTL, 최대 4석) |
| DELETE | `/api/reservations/{reservationId}/hold` | 🔒 | 좌석 선점 해제 |

**GET /api/shows/{showId}/seats**
```json
// Response 200
[
  {
    "id": 101,
    "seatCode": "R-01-05",
    "rowNum": 1,
    "colNum": 5,
    "status": "AVAILABLE",  // AVAILABLE | RESERVED | BLOCKED
    "grade": "VIP",
    "price": 150000,
    "remainingSeconds": 183  // 선점 중인 경우 남은 TTL(초), 없으면 null
  }
]
```

**POST /api/shows/{showId}/seats/hold**
```json
// Request (1~4석)
{ "seatIds": [101, 102] }

// Response 200
{
  "heldSeatIds": [101, 102],
  "failedSeatIds": [],
  "reservationId": 999,
  "expiresAt": 1719830400000  // epoch millis (5분 후)
}
```

> - 동일 회차에 이미 PENDING 예매가 있으면 **409 DUPLICATE_SEAT_HOLD**
> - 일부 좌석이라도 선점 실패하면 성공한 좌석도 전체 롤백 후 **409 SEAT_ALREADY_HELD**

---

### 5. 결제

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/payments/init` | 🔒 | 결제 초기화 (orderId 발급) |
| POST | `/api/payments/confirm` | 🔒 | 결제 승인 (Saga 패턴, Kafka 발행) |
| GET | `/api/payments/reservations/{reservationId}` | 🔒 | 결제 정보 조회 |

**POST /api/payments/init**
```json
// Request
{ "reservationId": 999 }

// Response 200
{
  "orderId": "stagepass-999-1719830000000",
  "amount": 300000,
  "orderName": "뮤지컬 레미제라블 R-01-05 외 1석"
}
```

**POST /api/payments/confirm**
```json
// Request (토스페이먼츠 결제창 완료 후 전달받은 값)
{
  "reservationId": 999,
  "paymentKey": "5zJ4xY7m0kODnyRpQWGrN2eqXgQVLKad82zMy826sJzE2Pvb",
  "orderId": "stagepass-999-1719830000000",
  "amount": 300000
}
// Response 200 — 이후 결제는 Kafka Saga로 비동기 처리
```

---

### 6. 예매 내역

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/reservations` | 🔒 | 내 예매 목록 (JOIN FETCH — N+1 방지) |
| GET | `/api/reservations/{reservationId}` | 🔒 | 예매 상세 조회 |
| DELETE | `/api/reservations/{reservationId}` | 🔒 | 예매 취소 (TossPayments 환불 + 취소 대기 알림 연동) |

**GET /api/reservations**
```json
// Response 200
[
  {
    "id": 999,
    "showId": 10,
    "performanceTitle": "뮤지컬 레미제라블",
    "showDatetime": "2025-07-01T19:30:00",
    "status": "CONFIRMED",  // PENDING | CONFIRMED | CANCELLED | EXPIRED
    "totalPrice": 300000,
    "reservedAt": "2025-06-01T10:00:00",
    "expiresAt": "2025-06-01T10:05:00",
    "seatCodes": ["R-01-05", "R-01-06"]
  }
]
```

---

### 7. 양도 게시판

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/transfers` | - | 양도 목록 조회 (`?showId=` 필터 가능) |
| POST | `/api/transfers` | 🔒 | 양도 게시글 등록 |
| GET | `/api/transfers/my` | 🔒 | 내가 올린 양도 글 목록 |
| POST | `/api/transfers/{transferId}/claim` | 🔒 | 양도 수락 (선착순, Redis SET NX) |
| DELETE | `/api/transfers/{transferId}` | 🔒 | 양도 게시글 취소 |

**POST /api/transfers**
```json
// Request
{
  "reservationId": 999,
  "price": 300000
}
```

---

### 8. 취소 대기

> 매진 공연에 대기를 걸어두면 예매 취소 발생 시 순번 순서대로 SSE 알림을 받습니다.
> 순번은 **Redis Sorted Set**(score = 등록 timestamp)으로 실시간 관리됩니다.

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/shows/{showId}/waitlist` | 🔒 | 취소 대기 등록 |
| GET | `/api/shows/{showId}/waitlist` | 🔒 | 내 대기 순번 / 상태 조회 |
| DELETE | `/api/shows/{showId}/waitlist` | 🔒 | 대기 취소 |

**POST /api/shows/{showId}/waitlist**
```json
// Response 200
{
  "rank": 3,     // 현재 대기 순번
  "total": 15,   // 전체 대기 인원
  "status": "WAITING"  // WAITING | NOTIFIED | NOT_IN_WAITLIST
}
```

> - 이미 대기 중이면 **409 WAITLIST_ALREADY_JOINED**
> - NOTIFIED 상태: 알림 수신 후 10분 내 예매 가능

---

### 9. 자리 교환

> 같은 회차 예매자끼리 좌석을 교환합니다.
> 수락 시 두 예매의 소유자가 **비관적 락(작은 ID 먼저)**으로 원자적으로 스왑됩니다.

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/exchanges` | 🔒 | 교환 제안 (내 예매 ID + 상대 예매 ID) |
| GET | `/api/exchanges/received` | 🔒 | 받은 교환 제안 목록 (PENDING) |
| GET | `/api/exchanges/sent` | 🔒 | 보낸 교환 제안 목록 |
| POST | `/api/exchanges/{exchangeId}/accept` | 🔒 | 교환 수락 |
| POST | `/api/exchanges/{exchangeId}/reject` | 🔒 | 교환 거절 |
| DELETE | `/api/exchanges/{exchangeId}` | 🔒 | 교환 제안 취소 (제안자만) |

**POST /api/exchanges**
```json
// Request
{
  "myReservationId": 999,
  "targetReservationId": 888
}

// Response 200
{
  "id": 55,
  "status": "PENDING",
  "proposerReservationId": 999,
  "receiverReservationId": 888,
  "expiresAt": "2025-06-02T10:00:00"  // 24시간 유효
}
```

> - 이미 PENDING 상태 교환이 있으면 **409 EXCHANGE_ALREADY_PENDING**
> - 본인 예매끼리 교환 시도 시 **400 EXCHANGE_SELF_PROPOSE**
> - 다른 회차 예매 간 교환 시도 시 **400 EXCHANGE_SHOW_MISMATCH**

---

### 10. 실시간 알림 (SSE)

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/notifications/subscribe` | 🔒 | SSE 구독 (실시간 알림 스트림) |

**GET /api/notifications/subscribe**
```
// Response: text/event-stream
data: {"type":"RESERVATION_CONFIRMED","message":"예매가 확정되었습니다."}
data: {"type":"PAYMENT_FAILED","message":"결제에 실패했습니다. 다시 시도해주세요."}
data: {"type":"QUEUE_ACTIVATED","message":"입장이 허가되었습니다. 지금 바로 좌석을 선택해주세요."}
data: {"type":"WAITLIST_NOTIFIED","message":"취소된 좌석이 생겼습니다! 10분 내로 예매를 완료해주세요."}
data: {"type":"TRANSFER_CLAIMED","message":"회원님의 티켓이 양도되었습니다."}
data: {"type":"EXCHANGE_COMPLETED","message":"자리 교환이 완료되었습니다."}
```

> **수평 확장**: Redis Pub/Sub(`PatternTopic("notification:*")`) 채널을 통해 다중 인스턴스 환경에서도 알림이 정상 전달됩니다.

---

## 어드민 (port 8081)

> 모든 어드민 API는 `/admin/auth/login`으로 발급받은 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

### 1. 인증

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/admin/auth/login` | 어드민 로그인 (ADMIN 권한 계정만 허용) |

**POST /admin/auth/login**
```json
// Request
{ "email": "admin@stagepass.com", "password": "adminpassword" }

// Response 200
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

---

### 2. 대시보드

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/admin/dashboard` | 통계 요약 (단일 집계 쿼리) |

**GET /admin/dashboard**
```json
// Response 200
{
  "totalReservations": 1500,
  "confirmedReservations": 1200,
  "cancelledReservations": 300,
  "totalRevenue": 360000000,
  "todayReservations": 42,
  "todayRevenue": 12600000
}
```

---

### 3. 공연 관리

> 공연 등록/수정/삭제는 사용자 API(`/api/performances`)와 경로를 공유하며 ADMIN 권한으로 보호됩니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/performances` | 공연 등록 |
| PUT | `/api/performances/{performanceId}` | 공연 수정 |
| DELETE | `/api/performances/{performanceId}` | 공연 삭제 |
| POST | `/api/performances/{performanceId}/shows` | 회차 등록 |

**POST /api/performances**
```json
// Request
{
  "title": "뮤지컬 레미제라블",
  "venue": "블루스퀘어 신한카드홀",
  "description": "...",
  "startDate": "2025-06-01",
  "endDate": "2025-08-31",
  "thumbnailUrl": "https://..."
}
```

**POST /api/performances/{performanceId}/shows**
```json
// Request
{
  "showDatetime": "2025-07-01T19:30:00",
  "totalSeats": 500
}
```

---

### 4. 회차 / 좌석 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/admin/performances/{performanceId}/shows/stats` | 회차별 예매 현황 (LEFT JOIN 단일 쿼리) |
| POST | `/admin/performances/shows/{showId}/zones` | 구역 및 좌석 일괄 생성 |
| PATCH | `/admin/performances/shows/{showId}/status` | 회차 상태 변경 |

**GET /admin/performances/{performanceId}/shows/stats**
```json
// Response 200
[
  {
    "showId": 10,
    "showDatetime": "2025-07-01T19:30:00",
    "totalSeats": 500,
    "confirmedCount": 377,
    "remainingSeats": 123,
    "status": "ON_SALE"
  }
]
```

**POST /admin/performances/shows/{showId}/zones**
```json
// Request
{
  "name": "VIP",
  "grade": "VIP",
  "price": 150000,
  "rowCount": 5,
  "colCount": 20
}
// → 5×20 = 100석 자동 생성 (seatCode: VIP-01-01 ~ VIP-05-20)
```

**PATCH /admin/performances/shows/{showId}/status?status=ON_SALE**
```
// Query param: SCHEDULED | ON_SALE | CLOSED | CANCELLED
// Response 200
```

---

## 공통 응답 포맷

```json
{
  "success": true,
  "data": { ... },
  "message": null
}
```

실패 시:
```json
{
  "success": false,
  "data": null,
  "message": "이미 선점된 좌석입니다."
}
```

---

## 주요 에러 코드

| 에러 코드 | HTTP | 설명 |
|-----------|------|------|
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰 |
| `EXPIRED_TOKEN` | 401 | 만료된 토큰 |
| `UNAUTHORIZED` | 401 | 인증 필요 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `USER_NOT_FOUND` | 404 | 사용자 없음 |
| `DUPLICATE_EMAIL` | 409 | 이미 사용 중인 이메일 |
| `PERFORMANCE_NOT_FOUND` | 404 | 공연 없음 |
| `SHOW_NOT_FOUND` | 404 | 회차 없음 |
| `SEAT_NOT_FOUND` | 404 | 좌석 없음 |
| `SEAT_ALREADY_HELD` | 409 | 이미 선점된 좌석 |
| `DUPLICATE_SEAT_HOLD` | 409 | 동일 회차 중복 선점 시도 |
| `RESERVATION_NOT_FOUND` | 404 | 예매 없음 |
| `RESERVATION_STATUS_INVALID` | 400 | 허용되지 않는 상태 전환 |
| `PAYMENT_FAILED` | 400 | 결제 실패 |
| `DUPLICATE_PAYMENT` | 409 | 중복 결제 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 결제 금액 불일치 |
| `TRANSFER_NOT_FOUND` | 404 | 양도 글 없음 |
| `TRANSFER_ALREADY_CLAIMED` | 409 | 이미 양도된 티켓 |
| `TRANSFER_SELF_CLAIM` | 400 | 본인 양도 글 수락 불가 |
| `WAITLIST_ALREADY_JOINED` | 409 | 이미 취소 대기 중 |
| `WAITLIST_NOT_FOUND` | 404 | 취소 대기 정보 없음 |
| `EXCHANGE_ALREADY_PENDING` | 409 | 이미 진행 중인 교환 제안 존재 |
| `EXCHANGE_NOT_PENDING` | 400 | 대기 중이 아닌 교환 제안 |
| `EXCHANGE_SELF_PROPOSE` | 400 | 본인에게 교환 제안 불가 |
| `EXCHANGE_SHOW_MISMATCH` | 400 | 다른 회차 좌석 교환 불가 |
| `EXCHANGE_FORBIDDEN` | 403 | 교환 제안 권한 없음 |

---

## Kafka 이벤트 흐름

```
결제 승인 요청
  → api: payment.requested 발행 (sync .get(5s) — 미발행 시 트랜잭션 롤백)
  → payment: TossPayments confirm API 호출 (CircuitBreaker 적용)
  → 성공: payment.completed 발행
      ├── reservation-group: 예매 상태 CONFIRMED
      ├── queue-payment-group: 다음 대기열 배치 activateNextBatch()
      └── notification: 예매 완료 SSE 알림
  → 실패: payment.failed 발행 (보상 트랜잭션)
      ├── seat-consumer: Redis 선점 해제
      └── notification: 결제 실패 SSE 알림

결제 취소
  → api: payment.cancel.requested 발행
  → payment: TossPayments cancel API 호출 (멱등 처리 — CANCELLED 상태면 API 재호출 없음)
  → reservation.cancelled 발행
  → notification: 취소 완료 SSE 알림

예매 취소 (사용자 직접)
  → api: 예매 상태 CANCELLED, Redis 선점 해제
  → WaitlistService.notifyNext() [REQUIRES_NEW]: 1순위 대기자 추출 + NOTIFIED 상태
  → waitlist.notified 발행
  → notification: 대기 1순위 유저에게 SSE 알림 (10분 내 예매 안내)

양도 수락
  → api: Redis SET NX 락 획득 → DB 예매 소유자 이전
  → transfer.claimed 발행
  → notification: 양도자에게 SSE 알림

자리 교환 수락
  → api: 비관적 락 (작은 ID 먼저) → 두 예매 소유자 원자적 스왑
  → exchange.completed 발행
  → notification: 제안자 + 수락자 양측 SSE 알림

대기열 활성화
  → 앞 사람 결제 완료 → queue-payment-group Consumer
  → activateNextBatch(): Redis Sorted Set 상위 N명 추출
  → queue.activated 발행
  → notification: 해당 유저에게 SSE 알림 (Redis Pub/Sub → 모든 인스턴스에 전달)
```
