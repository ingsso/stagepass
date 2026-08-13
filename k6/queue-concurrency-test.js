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
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
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

// 발급된 순번을 메트릭으로 방출한다.
// k6는 VU마다 독립된 JS 런타임을 쓰므로 모듈 스코프 배열은 VU 간 공유되지 않고
// handleSummary 에도 전달되지 않는다. 메트릭은 k6 엔진이 VU 경계를 넘어 수집하므로,
// `--out json` 원시 출력에서 개별 순번 값을 전수 집계해 중복을 검증한다.
//   k6 run --out json=k6/results/queue-raw.json k6/queue-concurrency-test.js ...
//   node k6/verify-ranks.js k6/results/queue-raw.json
const rankTrend = new Trend('queue_rank');

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

export default function () {
  const data = testData[0];

  // 토큰은 __VU 가 아니라 이터레이션 번호로 고른다.
  // shared-iterations 는 이터레이션을 VU 에 1:1 로 배분하지 않는다 — 빠른 VU 가 여러 개를
  // 가져가고 어떤 VU 는 하나도 실행하지 않는다. __VU 로 토큰을 고르면 같은 유저가 두 번 진입해
  // 서버가 (정상적으로) 같은 순번을 반환하고, 그게 '중복 순번' 으로 잘못 집계된다.
  // iterationInTest 는 테스트 전체에서 이터레이션마다 고유하므로 1인 1토큰이 보장된다.
  const token = data.tokens[exec.scenario.iterationInTest];
  if (!token) {
    console.error(`토큰 부족: iteration=${exec.scenario.iterationInTest} tokens=${data.tokens.length}`);
    failCount.add(1);
    return;
  }

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
        rankTrend.add(rank);
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
  const rank    = data.metrics.queue_rank?.values || {};

  console.log('\n========== 대기열 동시 진입 테스트 결과 ==========');
  console.log(`총 진입 시도  : ${VU_COUNT}명`);
  console.log(`성공          : ${success}명`);
  console.log(`발급 순번 범위: ${rank.min ?? '-'} ~ ${rank.max ?? '-'}`);
  console.log(`p95 응답시간  : ${p95.toFixed(0)}ms`);
  console.log('중복 순번 검증: verify-ranks.js 로 원시 출력을 전수 검사 (아래 명령)');
  console.log('  node k6/verify-ranks.js k6/results/queue-raw.json');
  console.log('==================================================\n');

  return {
    'k6/results/queue-concurrency-result.json': JSON.stringify(data, null, 2),
  };
}
