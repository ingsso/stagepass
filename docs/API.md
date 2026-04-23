# StagePass API 문서

화면(뷰) 기준으로 정리한 전체 API 목록입니다.

---

## 사용자 앱 (port 8080)

### 1. 회원가입 / 로그인 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 (Access Token + Refresh Token 발급) |
| POST | `/api/auth/reissue` | Access Token 재발급 |
| POST | `/api/auth/logout` | 로그아웃 (Refresh Token 삭제) |

---

### 2. 공연 목록 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/performances` | 공연 목록 조회 |
| GET | `/api/performances/{performanceId}` | 공연 상세 조회 |
| GET | `/api/performances/{performanceId}/shows` | 회차 목록 조회 |

---

### 3. 대기열 화면

> 예매 인기 공연은 대기열 진입 후 입장 허가를 받아야 좌석 선택으로 이동합니다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/shows/{showId}/queue` | 대기열 진입 |
| GET | `/api/shows/{showId}/queue/status` | 대기 순위 / 입장 허가 여부 조회 |
| DELETE | `/api/shows/{showId}/queue` | 대기열 이탈 |

---

### 4. 좌석 선택 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/shows/{showId}/seats` | 좌석 목록 조회 (Redis 캐싱, TTL 10s) |
| POST | `/api/shows/{showId}/seats/hold` | 좌석 임시 선점 (Redis SET NX, 5분 TTL) |
| DELETE | `/api/reservations/{reservationId}/hold` | 좌석 선점 해제 |

---

### 5. 결제 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/payments/init` | 결제 초기화 (orderId 발급) |
| POST | `/api/payments/confirm` | 결제 승인 (TossPayments API 연동) |
| GET | `/api/payments/reservations/{reservationId}` | 결제 정보 조회 |

---

### 6. 예매 내역 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/reservations` | 내 예매 목록 조회 |
| GET | `/api/reservations/{reservationId}` | 예매 상세 조회 |
| DELETE | `/api/reservations/{reservationId}` | 예매 취소 (TossPayments 환불 연동) |

---

### 7. 양도 게시판 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/transfers` | 양도 목록 조회 (`?showId=` 필터 가능) |
| POST | `/api/transfers` | 양도 게시글 등록 |
| GET | `/api/transfers/my` | 내가 올린 양도 글 목록 |
| POST | `/api/transfers/{transferId}/claim` | 양도 수락 (선착순, Redis SET NX) |
| DELETE | `/api/transfers/{transferId}` | 양도 게시글 취소 |

---

### 8. 알림 화면 (SSE)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/notifications/subscribe` | SSE 구독 (실시간 알림 스트림) |

> **제약**: 현재 SseEmitterRepository가 in-memory Map으로 구현되어 있어 다중 서버 인스턴스 환경에서는 알림 누락이 발생할 수 있습니다. 개선 방향: Redis Pub/Sub 또는 Redis Streams 연동.

---

## 어드민 (port 8081)

### 1. 어드민 로그인 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/admin/auth/login` | 어드민 로그인 |

---

### 2. 대시보드 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/admin/dashboard` | 통계 요약 (총 예매 수, 매출 등) |

---

### 3. 공연 관리 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/performances` | 공연 등록 |
| PUT | `/api/performances/{performanceId}` | 공연 수정 |
| DELETE | `/api/performances/{performanceId}` | 공연 삭제 |
| POST | `/api/performances/{performanceId}/shows` | 회차 등록 |

---

### 4. 회차 / 좌석 관리 화면

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/admin/performances/shows/{showId}/zones` | 구역 및 좌석 일괄 생성 |
| PATCH | `/admin/performances/shows/{showId}/status` | 회차 상태 변경 (OPEN/CLOSED 등) |
| GET | `/admin/performances/{performanceId}/shows/stats` | 회차별 예매 통계 조회 |

---

## Kafka 이벤트 흐름 (참고)

```
결제 승인 요청
  → payment-service: TossPayments confirm API 호출
  → Kafka: reservation.confirmed 발행
  → api-service: 예매 상태 CONFIRMED 업데이트
  → Kafka: notification.send 발행
  → notification-service: SSE 알림 전송

결제 실패 / 취소
  → payment-service: TossPayments cancel API 호출 (보상 트랜잭션)
  → Kafka: reservation.failed / reservation.cancelled 발행
  → api-service: 예매 상태 CANCELLED, 좌석 해제

양도 수락
  → api-service: Redis SET NX 락 획득 → DB 업데이트
  → Kafka: transfer.claimed 발행
  → notification-service: 양도자에게 SSE 알림 전송

대기열 활성화 (스케줄러, 30초 주기)
  → api-service: Redis Sorted Set에서 상위 N명 추출
  → Kafka: queue.activated 발행
  → notification-service: 해당 유저에게 SSE 알림 전송
```
