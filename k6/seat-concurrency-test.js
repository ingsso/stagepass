/**
 * 시나리오: 좌석 동시 선점 테스트
 *
 * 목표: 100명이 동일한 좌석 1개에 동시 선점 요청 시 정확히 1명만 성공해야 한다
 *
 * 실행:
 *   k6 run k6/seat-concurrency-test.js \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e SHOW_ID=1 \
 *     -e SEAT_ID=1 \
 *     -e DATA_FILE=k6/data/test-data.json
 *
 * 성공 기준:
 *   - http_req_failed rate = 0% (서버 에러 없음)
 *   - success_count = 1 (선점 성공 1명)
 *   - conflict_count = 99 (SEAT_ALREADY_HELD 99명)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 200, 409는 모두 예상된 응답 — http_req_failed 에서 제외
http.setResponseCallback(http.expectedStatuses(200, 409));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID  = __ENV.SHOW_ID  || '1';
const SEAT_ID  = __ENV.SEAT_ID  || '1';

// 사전 생성된 100명의 토큰 로드
const testData = new SharedArray('testData', function () {
  return [JSON.parse(open('./data/test-data.json'))];
});

// 커스텀 메트릭
const successCount  = new Counter('seat_hold_success');   // 선점 성공 횟수
const conflictCount = new Counter('seat_hold_conflict');  // 409 (이미 선점됨) 횟수
const failRate      = new Rate('seat_hold_fail_rate');    // 그 외 실패율

export const options = {
  scenarios: {
    concurrent_seat_hold: {
      executor: 'shared-iterations',
      vus: 100,           // 가상 유저 100명
      iterations: 100,    // 총 요청 100회 (VU당 1회)
      maxDuration: '30s',
    },
  },
  thresholds: {
    seat_hold_success:   ['count == 1'],   // 반드시 1명만 성공
    seat_hold_conflict:  ['count == 99'],  // 나머지 99명은 409
    http_req_failed:     ['rate < 0.01'],  // 서버 에러 1% 미만
    http_req_duration:   ['p(95) < 500'],  // 95%ile 응답 500ms 이내
  },
};

export default function () {
  const data   = testData[0];
  const token  = data.tokens[__VU - 1] || data.tokens[0];
  const seatId = Number(SEAT_ID) || data.seatIds[0];

  const res = http.post(
    `${BASE_URL}/api/shows/${SHOW_ID}/seats/hold`,
    JSON.stringify({ seatIds: [seatId] }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  if (res.status === 200) {
    successCount.add(1);
    check(res, {
      '선점 성공 응답 형식': (r) => JSON.parse(r.body).data?.heldSeatIds?.length > 0,
    });
    failRate.add(false);
  } else if (res.status === 409) {
    conflictCount.add(1);
    check(res, {
      'SEAT_ALREADY_HELD 에러코드': (r) => {
        const body = JSON.parse(r.body);
        return body.code === 'SEAT_ALREADY_HELD' || body.errorCode === 'SEAT_ALREADY_HELD';
      },
    });
    failRate.add(false);
  } else {
    failRate.add(true);
    console.error(`예상치 못한 응답 status=${res.status} body=${res.body}`);
  }
}

export function handleSummary(data) {
  const success  = data.metrics.seat_hold_success?.values?.count  || 0;
  const conflict = data.metrics.seat_hold_conflict?.values?.count || 0;
  const p95      = data.metrics.http_req_duration?.values?.['p(95)'] || 0;

  console.log('\n========== 좌석 동시 선점 테스트 결과 ==========');
  console.log(`총 요청 수    : ${success + conflict}명`);
  console.log(`선점 성공     : ${success}명 (기대값: 1명)`);
  console.log(`선점 실패(409): ${conflict}명 (기대값: 99명)`);
  console.log(`p95 응답시간  : ${p95.toFixed(0)}ms`);
  console.log(`중복 선점 발생: ${success > 1 ? '❌ 발생! (' + success + '명 성공)' : '✅ 없음'}`);
  console.log('=================================================\n');

  return {
    'k6/results/seat-concurrency-result.json': JSON.stringify(data, null, 2),
  };
}
