/**
 * 대기열 순번 중복 검증기
 *
 * k6 의 `--out json` 원시 출력(JSON Lines)에서 queue_rank 메트릭의 개별 값을
 * 전수 수집해 중복 순번을 검사한다.
 *
 * k6 는 VU 마다 독립된 JS 런타임을 사용하므로 테스트 스크립트 안의 모듈 스코프
 * 배열로는 VU 간 순번을 모을 수 없다(handleSummary 에도 전달되지 않는다).
 * 반면 메트릭 값은 k6 엔진이 VU 경계를 넘어 수집하므로, 원시 출력을 사후 집계하는
 * 이 방식이 실제로 동작하는 유일한 검증 경로다.
 *
 * 사용법:
 *   k6 run --out json=k6/results/queue-raw.json k6/queue-concurrency-test.js \
 *     -e BASE_URL=http://localhost:8080 -e SHOW_ID=1
 *   node k6/verify-ranks.js k6/results/queue-raw.json
 *
 * 종료 코드: 중복이 없으면 0, 있으면 1 (CI 에서 그대로 실패 처리 가능)
 */

const fs = require('fs');
const readline = require('readline');

const file = process.argv[2] || 'k6/results/queue-raw.json';

if (!fs.existsSync(file)) {
  console.error(`원시 출력 파일을 찾을 수 없습니다: ${file}`);
  console.error('k6 실행 시 --out json=<파일> 옵션을 지정했는지 확인하세요.');
  process.exit(2);
}

const ranks = [];

readline
  .createInterface({ input: fs.createReadStream(file), crlfDelay: Infinity })
  .on('line', (line) => {
    if (!line || line.indexOf('queue_rank') === -1) return;
    let row;
    try {
      row = JSON.parse(line);
    } catch (_) {
      return;
    }
    if (row.type === 'Point' && row.metric === 'queue_rank') {
      ranks.push(row.data.value);
    }
  })
  .on('close', () => {
    const counts = new Map();
    for (const r of ranks) counts.set(r, (counts.get(r) || 0) + 1);

    const duplicates = [...counts.entries()]
      .filter(([, n]) => n > 1)
      .sort((a, b) => a[0] - b[0]);

    const sorted = [...counts.keys()].sort((a, b) => a - b);
    const min = sorted[0];
    const max = sorted[sorted.length - 1];

    // 1..max 중 발급되지 않은 순번 (연결 실패 등으로 클라이언트가 응답을 못 받은 경우 발생 가능)
    const missing = [];
    for (let i = min; i <= max; i++) if (!counts.has(i)) missing.push(i);

    console.log('\n========== 대기열 순번 중복 검증 ==========');
    console.log(`원시 출력      : ${file}`);
    console.log(`발급된 순번 수 : ${ranks.length}건`);
    console.log(`고유 순번 수   : ${counts.size}건`);
    console.log(`순번 범위      : ${min} ~ ${max}`);
    console.log(`누락 순번      : ${missing.length}건${missing.length ? ' (' + missing.slice(0, 20).join(', ') + (missing.length > 20 ? ', ...' : '') + ')' : ''}`);

    if (duplicates.length === 0) {
      console.log('중복 순번      : ✅ 0건 — 모든 사용자가 서로 다른 순번을 발급받음');
      console.log('===========================================\n');
      process.exit(0);
    }

    const dupTotal = duplicates.reduce((s, [, n]) => s + (n - 1), 0);
    console.log(`중복 순번      : ❌ ${dupTotal}건 (${duplicates.length}개 순번이 중복 발급됨)`);
    for (const [rank, n] of duplicates.slice(0, 20)) {
      console.log(`  - 순번 ${rank}: ${n}명에게 발급`);
    }
    if (duplicates.length > 20) console.log(`  ... 외 ${duplicates.length - 20}개`);
    console.log('===========================================\n');
    process.exit(1);
  });
