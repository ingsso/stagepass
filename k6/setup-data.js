/**
 * 부하 테스트용 데이터 사전 생성 스크립트
 *
 * 실행: k6 run k6/setup-data.js
 *
 * 생성 항목:
 *  - 테스트 유저 100명 (test_user_0@stagepass.test ~ test_user_99@stagepass.test)
 *  - 공연 1개 + 회차 1개 + 구역 1개 + 좌석 10개
 *
 * 결과 출력: showId, seatIds, tokens → k6/data/test-data.json 에 저장 필요
 * (k6는 파일 쓰기를 지원하지 않으므로 콘솔 출력 후 수동 저장)
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  iterations: 1,
};

export default function () {
  // ── 1. 테스트 유저 생성 ──────────────────────────────
  const tokens = [];
  for (let i = 0; i < 100; i++) {
    const email = `test_user_${i}@stagepass.test`;

    // 회원가입
    http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({
      email,
      password: 'Test1234!',
      name: `테스트유저${i}`,
      phone: `010-0000-${String(i).padStart(4, '0')}`,
    }), { headers: { 'Content-Type': 'application/json' } });

    // 로그인
    const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
      email,
      password: 'Test1234!',
    }), { headers: { 'Content-Type': 'application/json' } });

    const body = JSON.parse(loginRes.body);
    tokens.push(body.data?.accessToken);
  }

  // ── 2. 공연 생성 ──────────────────────────────────────
  const perfRes = http.post(`${BASE_URL}/api/performances`, JSON.stringify({
    title: '부하테스트 공연',
    description: 'k6 부하 테스트용 공연입니다.',
    venue: '테스트 공연장',
    genre: 'MUSICAL',
    startDate: '2026-06-01',
    endDate: '2026-06-30',
  }), { headers: { 'Content-Type': 'application/json' } });

  const perf = JSON.parse(perfRes.body);
  const performanceId = perf.data?.id;
  console.log(`공연 생성: performanceId=${performanceId}`);

  // ── 3. 회차 생성 ──────────────────────────────────────
  const showRes = http.post(`${BASE_URL}/api/performances/${performanceId}/shows`, JSON.stringify({
    showDatetime: '2026-06-15T19:00:00',
    totalSeats: 100,
  }), { headers: { 'Content-Type': 'application/json' } });

  const show = JSON.parse(showRes.body);
  const showId = show.data?.id;
  console.log(`회차 생성: showId=${showId}`);

  // ── 4. 구역 + 좌석 생성 (admin API) ──────────────────
  const zoneRes = http.post(`${BASE_URL}/admin/performances/shows/${showId}/zones`, JSON.stringify({
    name: 'VIP',
    grade: 'VIP',
    price: 100000,
    rowCount: 2,
    colCount: 5,
  }), { headers: { 'Content-Type': 'application/json' } });

  const zone = JSON.parse(zoneRes.body);
  const zoneId = zone.data?.id;
  console.log(`구역 생성: zoneId=${zoneId}`);

  // ── 5. 좌석 목록 조회 ─────────────────────────────────
  const seatsRes = http.get(`${BASE_URL}/api/shows/${showId}/seats`);
  const seats = JSON.parse(seatsRes.body);
  const seatIds = seats.data?.map(s => s.id) || [];
  console.log(`좌석 목록: ${seatIds.join(', ')}`);

  // ── 결과 출력 (복사해서 k6/data/test-data.json 에 저장) ──
  const result = JSON.stringify({ showId, seatIds, tokens }, null, 2);
  console.log('\n=== test-data.json 내용 (복사해서 저장) ===');
  console.log(result);
  console.log('==========================================');
}
