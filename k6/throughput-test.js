/**
 * 시나리오: API 처리량(Throughput) 테스트
 *
 * 목표: 서버가 초당 몇 건의 요청을 안정적으로 처리할 수 있는지 측정
 *
 * 실행:
 *   k6 run k6/throughput-test.js \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e SHOW_ID=1
 *
 * 측정 항목:
 *   - 목표 RPS 달성 여부
 *   - p95 / p99 응답시간
 *   - 에러율
 *
 * 시나리오 (ramping-arrival-rate):
 *   Stage 1 — 워밍업:  0→50  RPS, 20s
 *   Stage 2 — 증가:   50→200 RPS, 30s
 *   Stage 3 — 유지:      200 RPS, 30s  ← 안정 처리량 측정 구간
 *   Stage 4 — 피크:  200→400 RPS, 20s  ← 한계점 탐색
 *   Stage 5 — 유지:      400 RPS, 20s
 *   Stage 6 — 쿨다운: 400→0  RPS, 10s
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID  = __ENV.SHOW_ID  || '1';

const testData = new SharedArray('testData', function () {
  return [JSON.parse(open('./data/test-data.json'))];
});

const errorRate    = new Rate('api_error_rate');
const seatListTime = new Trend('seat_list_duration', true);

export const options = {
  scenarios: {
    throughput: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 500,
      stages: [
        { target: 20,  duration: '15s' }, // 워밍업
        { target: 100, duration: '30s' }, // 증가
        { target: 100, duration: '30s' }, // 안정 처리량 측정 ← 핵심 구간
        { target: 200, duration: '20s' }, // 피크 탐색
        { target: 200, duration: '20s' }, // 피크 유지
        { target: 0,   duration: '10s' }, // 쿨다운
      ],
    },
  },
  thresholds: {
    api_error_rate:       ['rate < 0.05'],   // 에러율 5% 미만
    http_req_duration:    ['p(95) < 2000'],  // p95 2초 이내
    seat_list_duration:   ['p(99) < 3000'],  // p99 3초 이내
  },
};

export default function () {
  const data  = testData[0];
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];

  const res = http.get(
    `${BASE_URL}/api/shows/${SHOW_ID}/seats`,
    {
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    '좌석 목록 반환': (r) => {
      if (!r.body) return false;
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.data) && body.data.length > 0;
      } catch (_) { return false; }
    },
  });

  seatListTime.add(res.timings.duration);
  errorRate.add(!ok);
}

export function handleSummary(data) {
  const p95    = data.metrics.http_req_duration?.values?.['p(95)']   || 0;
  const p99    = data.metrics.http_req_duration?.values?.['p(99)']   || 0;
  const rps    = data.metrics.http_reqs?.values?.rate                 || 0;
  const total  = data.metrics.http_reqs?.values?.count                || 0;
  const errors = data.metrics.api_error_rate?.values?.rate            || 0;

  console.log('\n========== API 처리량(Throughput) 테스트 결과 ==========');
  console.log(`총 요청 수      : ${total.toLocaleString()}건`);
  console.log(`최대 RPS        : ${rps.toFixed(1)} req/s`);
  console.log(`p95 응답시간    : ${p95.toFixed(0)}ms`);
  console.log(`p99 응답시간    : ${p99.toFixed(0)}ms`);
  console.log(`에러율          : ${(errors * 100).toFixed(2)}%`);
  console.log('========================================================\n');

  return {
    'k6/results/throughput-result.json': JSON.stringify(data, null, 2),
  };
}
