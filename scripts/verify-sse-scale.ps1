# Redis Pub/Sub SSE 수평 확장 검증 스크립트
# 전제: docker-compose -f docker-compose-scale.yml up -d --build 가 완료된 상태
#
# 검증 시나리오:
#   userId=1 → notification-1(8083) 직접 연결
#   userId=2 → notification-2(8084) 직접 연결
#   Redis PUBLISH → 양쪽 인스턴스가 브로드캐스트 수신 → 해당 userId SSE 연결 보유 인스턴스만 전송
#   Kafka 발행 → 어느 인스턴스든 컨슘 → Redis broadcast → 올바른 인스턴스 SSE 전송

param(
    [int]$ConnectWait = 4,   # SSE 연결 초기화 대기(초)
    [int]$EventWait   = 3,   # 이벤트 수신 대기(초)
    [int]$KafkaWait   = 6    # Kafka 처리 대기(초)
)

$ErrorActionPreference = "Continue"

function Write-Step([string]$msg) { Write-Host "`n$msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host "  [PASS] $msg" -ForegroundColor Green }
function Write-Fail([string]$msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Write-Info([string]$msg) { Write-Host "  $msg" -ForegroundColor Gray }

$ts    = Get-Date -Format 'HHmmss'
$out1  = "$env:TEMP\sse_inst1_$ts.txt"
$out2  = "$env:TEMP\sse_inst2_$ts.txt"
$out3  = "$env:TEMP\sse_nginx_$ts.txt"
$passed = 0
$failed = 0

function Check([bool]$ok, [string]$okMsg, [string]$failMsg) {
    if ($ok) { Write-Ok $okMsg;   $script:passed++ }
    else      { Write-Fail $failMsg; $script:failed++ }
}

Write-Host "============================================================" -ForegroundColor Yellow
Write-Host "  Redis Pub/Sub SSE 수평 확장 검증" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow

# ── 1. SSE 구독 (각 인스턴스에 직접 연결) ────────────────────────────
Write-Step "[1] SSE 구독 시작"
Write-Info "userId=1 → notification-1(8083), userId=2 → notification-2(8084)"

$proc1 = Start-Process -FilePath "curl.exe" `
    -ArgumentList @("-sN", "--max-time", "60",
                    "http://localhost:8083/api/notifications/subscribe?userId=1") `
    -RedirectStandardOutput $out1 -PassThru -NoNewWindow

$proc2 = Start-Process -FilePath "curl.exe" `
    -ArgumentList @("-sN", "--max-time", "60",
                    "http://localhost:8084/api/notifications/subscribe?userId=2") `
    -RedirectStandardOutput $out2 -PassThru -NoNewWindow

Write-Info "연결 초기화 대기 ($ConnectWait 초)..."
Start-Sleep $ConnectWait

$c1 = (Get-Content $out1 -ErrorAction SilentlyContinue) -join " "
$c2 = (Get-Content $out2 -ErrorAction SilentlyContinue) -join " "

Check ($c1 -match "CONNECTED") `
    "notification-1: CONNECTED 이벤트 수신" `
    "notification-1: CONNECTED 없음 — 서비스 미시작 또는 포트 오류"
Check ($c2 -match "CONNECTED") `
    "notification-2: CONNECTED 이벤트 수신" `
    "notification-2: CONNECTED 없음 — 서비스 미시작 또는 포트 오류"

# ── 2. Redis Pub/Sub 직접 발행 (핵심 검증) ──────────────────────────
Write-Step "[2] Redis Pub/Sub 직접 발행 — 인스턴스 간 브로드캐스트 검증"
Write-Info "notification:1 발행 → inst-1이 SSE 전송해야 함 (inst-2는 emitter 없어 skip)"
Write-Info "notification:2 발행 → inst-2가 SSE 전송해야 함"

$msg1 = '{"userId":1,"type":"PUBSUB_TEST","message":"Redis Pub/Sub 브로드캐스트 검증"}'
$msg2 = '{"userId":2,"type":"PUBSUB_TEST","message":"Redis Pub/Sub 브로드캐스트 검증"}'

docker exec stagepass-redis redis-cli PUBLISH "notification:1" $msg1 | Out-Null
docker exec stagepass-redis redis-cli PUBLISH "notification:2" $msg2 | Out-Null

Start-Sleep $EventWait

$c1 = (Get-Content $out1 -ErrorAction SilentlyContinue) -join " "
$c2 = (Get-Content $out2 -ErrorAction SilentlyContinue) -join " "

Check ($c1 -match "PUBSUB_TEST") `
    "notification-1: Redis→SSE 수신 (userId=1)" `
    "notification-1: PUBSUB_TEST 미수신 — RedisNotificationSubscriber 또는 Redis 연결 확인"
Check ($c2 -match "PUBSUB_TEST") `
    "notification-2: Redis→SSE 수신 (userId=2)" `
    "notification-2: PUBSUB_TEST 미수신 — RedisNotificationSubscriber 또는 Redis 연결 확인"

# ── 3. 크로스 인스턴스 검증 ──────────────────────────────────────────
Write-Step "[3] 크로스 인스턴스 검증"
Write-Info "notification:1 발행 → inst-2 브로드캐스트 수신 → emitter 없어 전송 안 함 (inst-1만 전송)"
Write-Info "즉, inst-2의 stdout에 CROSS_TEST 없어야 정상"

$cross = '{"userId":1,"type":"CROSS_TEST","message":"크로스 인스턴스 격리 확인"}'
docker exec stagepass-redis redis-cli PUBLISH "notification:1" $cross | Out-Null

Start-Sleep $EventWait

$c1 = (Get-Content $out1 -ErrorAction SilentlyContinue) -join " "
$c2 = (Get-Content $out2 -ErrorAction SilentlyContinue) -join " "

Check ($c1 -match "CROSS_TEST") `
    "notification-1: CROSS_TEST SSE 전송 (userId=1 emitter 보유)" `
    "notification-1: CROSS_TEST 미수신"
Check (-not ($c2 -match "CROSS_TEST")) `
    "notification-2: CROSS_TEST 미전송 (userId=1 emitter 없음 — 격리 정상)" `
    "notification-2: CROSS_TEST가 잘못 수신됨 — 격리 실패"

# ── 4. Kafka 엔드-투-엔드 ─────────────────────────────────────────────
Write-Step "[4] Kafka 엔드-투-엔드 — notification.send 토픽 발행"
Write-Info "Kafka consumer가 어느 인스턴스를 선택하든 Redis broadcast → 올바른 인스턴스 SSE 전송"

# kafka-console-producer는 한 줄을 읽고 전송 후 EOF에서 종료
$km1 = '{"userId":1,"type":"E2E_TEST","message":"Kafka→Redis→SSE 플로우 userId=1"}'
$km2 = '{"userId":2,"type":"E2E_TEST","message":"Kafka→Redis→SSE 플로우 userId=2"}'

# 파이프로 stdin 전달
$km1 | docker exec -i stagepass-kafka kafka-console-producer `
    --bootstrap-server kafka:29092 --topic notification.send 2>$null
$km2 | docker exec -i stagepass-kafka kafka-console-producer `
    --bootstrap-server kafka:29092 --topic notification.send 2>$null

Write-Info "Kafka 처리 대기 ($KafkaWait 초)..."
Start-Sleep $KafkaWait

$c1 = (Get-Content $out1 -ErrorAction SilentlyContinue) -join " "
$c2 = (Get-Content $out2 -ErrorAction SilentlyContinue) -join " "

Check ($c1 -match "E2E_TEST") `
    "notification-1: Kafka→SSE E2E 정상 (userId=1)" `
    "notification-1: E2E_TEST 미수신 — Kafka consumer 또는 NotificationConsumer 로그 확인"
Check ($c2 -match "E2E_TEST") `
    "notification-2: Kafka→SSE E2E 정상 (userId=2)" `
    "notification-2: E2E_TEST 미수신 — Kafka consumer 또는 NotificationConsumer 로그 확인"

# ── 5. Nginx 로드밸런서 경유 SSE ──────────────────────────────────────
Write-Step "[5] Nginx(8090) 로드밸런서 경유 SSE 연결"

$proc3 = Start-Process -FilePath "curl.exe" `
    -ArgumentList @("-sN", "--max-time", "15",
                    "http://localhost:8090/api/notifications/subscribe?userId=3") `
    -RedirectStandardOutput $out3 -PassThru -NoNewWindow

Start-Sleep $ConnectWait

$c3 = (Get-Content $out3 -ErrorAction SilentlyContinue) -join " "
Check ($c3 -match "CONNECTED") `
    "Nginx(8090): SSE 연결 및 CONNECTED 이벤트 수신" `
    "Nginx(8090): CONNECTED 없음 — nginx 설정 또는 upstream 확인"

# ── 정리 ─────────────────────────────────────────────────────────────
$proc1, $proc2, $proc3 | Where-Object { $_ -and -not $_.HasExited } | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "`n============================================================" -ForegroundColor Yellow
Write-Host "  결과: PASS $passed / FAIL $failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "============================================================" -ForegroundColor Yellow

if ($failed -gt 0) {
    Write-Host "`n  디버깅 명령어:" -ForegroundColor Gray
    Write-Host "    docker logs notification-1 --tail 50" -ForegroundColor Gray
    Write-Host "    docker logs notification-2 --tail 50" -ForegroundColor Gray
    Write-Host "    docker exec stagepass-redis redis-cli PUBSUB CHANNELS 'notification:*'" -ForegroundColor Gray
}

Write-Host "`n  SSE 스트림 로그:" -ForegroundColor Gray
Write-Host "    inst-1: $out1" -ForegroundColor Gray
Write-Host "    inst-2: $out2" -ForegroundColor Gray
Write-Host "    nginx:  $out3" -ForegroundColor Gray
