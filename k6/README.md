# 부하 테스트 (k6)

## 사전 준비

### 1. 인프라 + API 서버 실행

```bash
# 인프라 (PostgreSQL, Redis, Kafka)
docker-compose up -d

# API 서버
./gradlew :api:bootRun --args='--spring.profiles.active=local'
```

### 2. 테스트 데이터 생성

```bash
k6 run k6/setup-data.js -e BASE_URL=http://localhost:8080
```

콘솔에 출력된 JSON을 복사해서 `k6/data/test-data.json` 으로 저장:

```json
{
  "showId": 1,
  "seatIds": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
  "tokens": ["eyJ...", "eyJ...", ...]
}
```

---

## 테스트 실행

### 시나리오 1: 좌석 동시 선점 (핵심 테스트)

> 100명이 동일한 좌석 1개에 동시 선점 요청 → 정확히 1명만 성공해야 한다

```bash
k6 run k6/seat-concurrency-test.js \
  -e BASE_URL=http://localhost:8080 \
  -e SHOW_ID=1 \
  -e SEAT_ID=1
```

**성공 기준**

| 항목 | 기대값 |
|------|--------|
| 선점 성공 | 1명 |
| 409 (이미 선점됨) | 99명 |
| 중복 선점 | 0건 |
| p95 응답시간 | 500ms 이내 |

---

### 시나리오 2: 대기열 동시 진입

> 50명이 동시에 대기열 진입 → 중복 순번 없이 1~50번 발급되어야 한다

```bash
k6 run k6/queue-concurrency-test.js \
  -e BASE_URL=http://localhost:8080 \
  -e SHOW_ID=1
```

**성공 기준**

| 항목 | 기대값 |
|------|--------|
| 진입 성공 | 50명 |
| 중복 순번 | 0건 |
| p95 응답시간 | 500ms 이내 |

---

## 결과 파일

테스트 완료 후 `k6/results/` 에 JSON 결과 파일이 생성됩니다.

```
k6/results/
├── seat-concurrency-result.json
└── queue-concurrency-result.json
```
