# SSE 수평 확장 검증 결과 (Redis Pub/Sub)

`notification` 인스턴스를 2개 띄운 상태에서 **한쪽에 SSE 로 연결한 클라이언트가
다른 쪽 인스턴스의 Kafka Consumer 가 처리한 알림을 받는지** 실제로 확인한 기록입니다.

- 실행일: **2026-08-18**
- 실행 스크립트: [`scripts/verify-sse-scale.ps1`](../../scripts/verify-sse-scale.ps1)
- 환경 정의: [`docker-compose-scale.yml`](../../docker-compose-scale.yml)
- 결과: **PASS 12 / FAIL 0**, 연속 2회 동일

## 구성

```
                    ┌─ notification-1 (:8083) ─┐
클라이언트 ─ SSE ──▶│                          │◀── Kafka notification.send (파티션 3)
                    └─ notification-2 (:8084) ─┘
                              ▲   ▲
                              └───┴── Redis PatternTopic("notification:*")
```

| 항목 | 값 |
|------|-----|
| 인스턴스 | notification-1 (8083) / notification-2 (8084), 동일 이미지 2개 |
| 로드밸런서 | nginx (8090) → 두 인스턴스 upstream |
| Kafka | `notification.send` 토픽 파티션 3, consumer group `notification-group` 공유 |
| Redis | 두 인스턴스 모두 `notification:*` 패턴 구독 (`PUBSUB NUMPAT` = 1, 구독자 2) |
| 호스트 | Windows 11 / Docker Desktop, Windows PowerShell 5.1 |

## 검증 항목과 결과

| # | 검증 내용 | 결과 |
|---|-----------|------|
| 1 | 두 인스턴스 각각 SSE 연결 수립 (`CONNECTED`) | PASS |
| 2 | Redis 발행 → 해당 유저 emitter 보유 인스턴스가 SSE 전송 | PASS |
| 3 | 브로드캐스트 격리 — emitter 없는 인스턴스는 전송 안 함 | PASS |
| 4 | Kafka `notification.send` → Redis → SSE E2E (양쪽 유저) | PASS |
| **4-B** | **컨슈머와 SSE 연결이 서로 다른 인스턴스인 상태에서 알림 전달** | **PASS** |
| 5 | nginx 로드밸런서 경유 SSE 연결 | PASS |

## 핵심 시나리오 (4-B) 증거

파티션 배정은 실행마다 달라지므로, 프로브 메시지로 **어느 인스턴스가 그 key 를 컨슘하는지 먼저 특정**한 뒤
**반대쪽 인스턴스에 SSE 를 연결**하고 같은 key 로 다시 발행했습니다.

컨슈머는 notification-2 (userId=7 이벤트를 2건 모두 소비, notification-1 은 0건):

```
notification-2 | 10:50:50.729 INFO  c.s.n.consumer.NotificationConsumer : [Notification] received userId=7 type=PROBE
notification-2 | 10:51:03.839 INFO  c.s.n.consumer.NotificationConsumer : [Notification] received userId=7 type=CROSS_E2E
notification-1 | (userId=7 수신 로그 0건)
```

SSE 클라이언트는 notification-1(8083) 에 연결되어 있었고, 그 스트림에 알림이 도착했습니다:

```
event:CONNECTED
data:SSE connected

event:CROSS_E2E
data:다른 인스턴스 컨슈머가 처리한 알림
```

즉 **Kafka 컨슈머(notification-2) → Redis Publish(`notification:7`) → 브로드캐스트 수신(notification-1) → SSE push** 경로가
실제로 동작합니다.

## 실행 출력 (1회차)

```
============================================================
  Redis Pub/Sub SSE 수평 확장 검증
============================================================

[1] SSE 구독 시작
  userId=1 → notification-1(8083), userId=2 → notification-2(8084)
  연결 초기화 대기 (4 초)...
  [PASS] notification-1: CONNECTED 이벤트 수신
  [PASS] notification-2: CONNECTED 이벤트 수신

[2] Redis Pub/Sub 직접 발행 — 인스턴스 간 브로드캐스트 검증
  notification:1 발행 → inst-1이 SSE 전송해야 함 (inst-2는 emitter 없어 skip)
  notification:2 발행 → inst-2가 SSE 전송해야 함
  [PASS] notification-1: Redis→SSE 수신 (userId=1)
  [PASS] notification-2: Redis→SSE 수신 (userId=2)

[3] 크로스 인스턴스 검증
  notification:1 발행 → inst-2 브로드캐스트 수신 → emitter 없어 전송 안 함 (inst-1만 전송)
  즉, inst-2의 stdout에 CROSS_TEST 없어야 정상
  [PASS] notification-1: CROSS_TEST SSE 전송 (userId=1 emitter 보유)
  [PASS] notification-2: CROSS_TEST 미전송 (userId=1 emitter 없음 — 격리 정상)

[4] Kafka 엔드-투-엔드 — notification.send 토픽 발행
  Kafka consumer가 어느 인스턴스를 선택하든 Redis broadcast → 올바른 인스턴스 SSE 전송
  Kafka 처리 대기 (6 초)...
  [PASS] notification-1: Kafka→SSE E2E 정상 (userId=1)
  [PASS] notification-2: Kafka→SSE E2E 정상 (userId=2)

[4-B] 크로스 인스턴스 Kafka E2E — 컨슈머와 SSE 연결이 서로 다른 인스턴스
  파티션 배정은 실행마다 달라지므로 프로브로 컨슈머를 먼저 특정한 뒤
  SSE 는 반대쪽 인스턴스에 연결하고 같은 key 로 다시 발행합니다.
  [PASS] userId=7 이벤트 컨슈머 = notification-2 (inst1=0 / inst2=1 건)
  SSE 연결 대상 = notification-1 (8083) — 컨슈머(notification-2)와 다른 인스턴스
  [PASS] notification-1 SSE 클라이언트가 notification-2 컨슈머의 알림 수신
  [PASS] SSE 연결측 notification-1 은 Kafka 이벤트를 한 건도 컨슘하지 않음 (inst1=0 / inst2=2 건)

[5] Nginx(8090) 로드밸런서 경유 SSE 연결
  [PASS] Nginx(8090): SSE 연결 및 CONNECTED 이벤트 수신

============================================================
  결과: PASS 12 / FAIL 0
============================================================

  SSE 스트림 로그:
    inst-1: C:\Users\elice\AppData\Local\Temp\sse_inst1_195025.txt
    inst-2: C:\Users\elice\AppData\Local\Temp\sse_inst2_195025.txt
    nginx:  C:\Users\elice\AppData\Local\Temp\sse_nginx_195025.txt
```

## 반복 실행 (2회차, 컨테이너 재생성 없이 연속 실행)

```
  [PASS] notification-1: CONNECTED 이벤트 수신
  [PASS] notification-2: CONNECTED 이벤트 수신
  [PASS] notification-1: Redis→SSE 수신 (userId=1)
  [PASS] notification-2: Redis→SSE 수신 (userId=2)
  [PASS] notification-1: CROSS_TEST SSE 전송 (userId=1 emitter 보유)
  [PASS] notification-2: CROSS_TEST 미전송 (userId=1 emitter 없음 — 격리 정상)
  [PASS] notification-1: Kafka→SSE E2E 정상 (userId=1)
  [PASS] notification-2: Kafka→SSE E2E 정상 (userId=2)
  [PASS] userId=7 이벤트 컨슈머 = notification-2 (inst1=0 / inst2=3 건)
  [PASS] notification-1 SSE 클라이언트가 notification-2 컨슈머의 알림 수신
  [PASS] SSE 연결측 notification-1 은 Kafka 이벤트를 한 건도 컨슘하지 않음 (inst1=0 / inst2=4 건)
  [PASS] Nginx(8090): SSE 연결 및 CONNECTED 이벤트 수신
  결과: PASS 12 / FAIL 0
```

2회차는 1회차에서 끊긴 emitter 가 남아 있는 상태로 실행됩니다 — 죽은 emitter 에 대한
`[SSE] send failed ... removing emitter` WARN 이 찍히지만 살아 있는 연결로는 정상 전달됩니다.

## 이번 검증에서 발견해 고친 문제

| 문제 | 원인 | 조치 |
|------|------|------|
| 스크립트가 파싱조차 안 됨 | UTF-8 BOM 없는 `.ps1` 을 Windows PowerShell 5.1 이 ANSI 로 읽어 한글 주석에서 파서 에러 | 파일을 UTF-8 with BOM 으로 저장 |
| Redis 발행 페이로드가 `{userId:1,...}` 로 깨짐 | PowerShell 이 네이티브 exe 인자에서 큰따옴표를 벗겨냄 | JSON 을 base64 로 감싸 컨테이너 안에서 복원 |
| Kafka 메시지가 전부 포이즌 필 처리 | PowerShell 이 네이티브 stdin 파이프에 UTF-8 BOM 을 붙임 → `String` 페이로드 앞에 `﻿` | 위와 동일하게 base64 경유 |
| **죽은 emitter 하나가 같은 유저의 다른 연결까지 막음** | `SseNotificationService.sendToUser` 가 `IOException` 만 잡아서, 이미 에러난 AsyncContext 가 던지는 `IllegalStateException` 이 루프 밖으로 탈출 | `catch (Exception)` 으로 확장하고 `completeWithError` 도 감쌈 |

마지막 항목은 스크립트가 아니라 **애플리케이션 버그**입니다. 재연결(같은 userId 로 새 SSE 연결)이 잦은 환경에서
직전 연결이 남아 있으면 새 연결이 알림을 못 받는 문제였고, 이번 검증 중 2회차 실행이 실패하면서 드러났습니다.

## 재현 방법

```powershell
./gradlew :notification:bootJar
docker compose -f docker-compose-scale.yml up -d --build   # 호스트 9092 가 점유 중이면 $env:KAFKA_EXTERNAL_PORT="9095"
./scripts/verify-sse-scale.ps1
```

정리: `docker compose -p <프로젝트> -f docker-compose-scale.yml down -v`
