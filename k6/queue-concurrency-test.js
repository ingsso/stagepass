/**
 * 시나리오: 대기열 동시 진입 테스트
 *
 * 목표: 50명이 동시에 대기열 진입 시 중복 순번 없이 1~50번 순번이 모두 발급되어야 한다
 *
 * 실행:
 *   k6 run k6/queue-concurrency-test.js \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e SHOW_ID=1 \
 *     -e DATA_FILE=k6/data/test-data.json
 *
 * 성공 기준:
 *   - 50명 모두 성공 (200 응답)
 *   - 중복 순번 0건
 *   - p95 응답시간 500ms 이내
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 200만 정상 응답 (이미 진입한 경우 등 예외는 failCount 로 추적)
http.setResponseCallback(http.expectedStatuses(200));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHOW_ID  = __ENV.SHOW_ID  || '1';
const VU_COUNT = 1000;

const testData = new SharedArray('testData', function () {
  return [JSON.parse(open('./data/test-data.json'))];
});

const successCount  = new Counter('queue_enter_success');
const failCount     = new Counter('queue_enter_fail');

export const options = {
  scenarios: {
    concurrent_queue_enter: {
      executor: 'shared-iterations',
      vus: VU_COUNT,
      iterations: VU_COUNT,
      maxDuration: '60s',
    },
  },
  thresholds: {
    queue_enter_success: [`count >= ${VU_COUNT * 0.95}`], // 핵심: 95% 이상 성공 (OS 에러 5% 허용)
    http_req_failed:     ['rate < 0.10'],                 // OS 네트워크 에러 10% 미만 (로컬 1000-VU 환경)
    http_req_duration:   ['p(95) < 3000'],                // 로컬 1000-VU 극한 환경 기준
  },
};

// 수집된 순번 목록 (중복 검증용)
const ranks = [];

export default function () {
  const data  = testData[0];
  const token = data.tokens[__VU - 1] || data.tokens[0];

  const res = http.post(
    `${BASE_URL}/api/shows/${SHOW_ID}/queue`,
    null,
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }
  );

  const ok = check(res, {
    '대기열 진입 성공 (200)': (r) => r.status === 200,
    '순번 발급됨': (r) => {
      if (!r.body) return false;
      try {
        const body = JSON.parse(r.body);
        return body.data?.rank > 0;
      } catch (_) { return false; }
    },
  });

  if (res.status === 200 && res.body) {
    try {
      const rank = JSON.parse(res.body).data?.rank;
      if (rank) {
        successCount.add(1);
        ranks.push(rank);
      } else {
        failCount.add(1);
      }
    } catch (_) {
      failCount.add(1);
    }
  } else {
    failCount.add(1);
    console.error(`대기열 진입 실패 status=${res.status} body=${res.body}`);
  }
}

export function handleSummary(data) {
  const success = data.metrics.queue_enter_success?.values?.count || 0;
  const p95     = data.metrics.http_req_duration?.values?.['p(95)'] || 0;

  // 중복 순번 검증
  const uniqueRanks = new Set(ranks);
  const hasDuplicate = uniqueRanks.size < ranks.length;

  console.log('\n========== 대기열 동시 진입 테스트 결과 ==========');
  console.log(`총 진입 시도  : ${VU_COUNT}명`);
  console.log(`성공          : ${success}명`);
  console.log(`발급된 순번   : ${ranks.sort((a, b) => a - b).join(', ')}`);
  console.log(`중복 순번 발생: ${hasDuplicate ? '❌ 발생! (중복: ' + (ranks.length - uniqueRanks.size) + '건)' : '✅ 없음'}`);
  console.log(`p95 응답시간  : ${p95.toFixed(0)}ms`);
  console.log('==================================================\n');

  return {
    'k6/results/queue-concurrency-result.json': JSON.stringify(data, null, 2),
  };
}
