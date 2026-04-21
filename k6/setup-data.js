/**
 * 부하 테스트용 데이터 사전 생성 스크립트
 *
 * 실행:
 *   k6 run k6/setup-data.js \
 *     -e BASE_URL=http://localhost:8080 \
 *     -e ADMIN_URL=http://localhost:8081 \
 *     -e ADMIN_EMAIL=admin@stagepass.test \
 *     -e ADMIN_PASSWORD=Admin1234!
 *
 * 완료 후 출력된 JSON을 k6/data/test-data.json 에 저장
 */

import http from 'k6/http';

const BASE_URL      = __ENV.BASE_URL       || 'http://localhost:8080';
const ADMIN_URL     = __ENV.ADMIN_URL      || 'http://localhost:8081';
const ADMIN_EMAIL   = __ENV.ADMIN_EMAIL    || 'admin@stagepass.test';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'Admin1234!';

export const options = { vus: 1, iterations: 1 };

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeader(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
}

function parse(res, label) {
  if (!res.body || res.body.trim() === '') {
    console.error(`[${label}] 빈 응답 status=${res.status} url=${res.url}`);
    return null;
  }
  try {
    return JSON.parse(res.body);
  } catch (e) {
    console.error(`[${label}] JSON 파싱 실패 status=${res.status} body=${res.body.substring(0, 200)}`);
    return null;
  }
}

export default function () {

  // ── 1. API 서버 일반 유저로 공연/회차 생성 ─────────────────
  const userEmail = 'setup_user@stagepass.test';

  http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({
    email: userEmail, password: 'Test1234!', name: '셋업유저', phone: '010-9999-0000',
  }), { headers: JSON_HEADERS });

  const userLoginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    email: userEmail, password: 'Test1234!',
  }), { headers: JSON_HEADERS });

  const userLogin = parse(userLoginRes, '유저 로그인');
  if (!userLogin?.data?.accessToken) {
    console.error('유저 로그인 실패 — 종료');
    return;
  }
  const userToken = userLogin.data.accessToken;
  console.log('유저 로그인 성공');

  // ── 2. 공연 생성 ──────────────────────────────────────────
  const perfRes = http.post(`${BASE_URL}/api/performances`, JSON.stringify({
    title: '부하테스트 공연', genre: 'MUSICAL', description: 'k6 테스트용',
    venueName: '테스트 공연장', venueAddress: '서울시 강남구', runningTime: 120,
  }), { headers: authHeader(userToken) });

  const perf = parse(perfRes, '공연 생성');
  const performanceId = perf?.data?.id;
  if (!performanceId) {
    console.error(`공연 생성 실패 status=${perfRes.status} body=${perfRes.body}`);
    return;
  }
  console.log(`공연 생성 성공 performanceId=${performanceId}`);

  // ── 3. 회차 생성 ──────────────────────────────────────────
  const showRes = http.post(
    `${BASE_URL}/api/performances/${performanceId}/shows`,
    JSON.stringify({ showDatetime: '2026-12-15T19:00:00', totalSeats: 100 }),
    { headers: authHeader(userToken) }
  );

  const show = parse(showRes, '회차 생성');
  const showId = show?.data?.id;
  if (!showId) {
    console.error(`회차 생성 실패 status=${showRes.status} body=${showRes.body}`);
    return;
  }
  console.log(`회차 생성 성공 showId=${showId}`);

  // ── 4. Admin 서버 로그인 ───────────────────────────────────
  const adminLoginRes = http.post(`${ADMIN_URL}/admin/auth/login`, JSON.stringify({
    email: ADMIN_EMAIL, password: ADMIN_PASSWORD,
  }), { headers: JSON_HEADERS });

  const adminLogin = parse(adminLoginRes, 'Admin 로그인');
  if (!adminLogin?.data?.accessToken) {
    console.error(`Admin 로그인 실패 status=${adminLoginRes.status} body=${adminLoginRes.body}`);
    console.error('admin 서버가 실행 중인지, admin@stagepass.test 계정이 존재하는지 확인하세요');
    return;
  }
  const adminToken = adminLogin.data.accessToken;
  console.log('Admin 로그인 성공');

  // ── 5. 구역 + 좌석 생성 (admin 서버, 10x10 = 100석) ────────
  const zoneRes = http.post(
    `${ADMIN_URL}/admin/performances/shows/${showId}/zones`,
    JSON.stringify({ name: 'VIP', grade: 'VIP', price: 100000, rowCount: 10, colCount: 10 }),
    { headers: authHeader(adminToken) }
  );

  const zone = parse(zoneRes, '구역 생성');
  if (zoneRes.status !== 200) {
    console.error(`구역 생성 실패 status=${zoneRes.status} body=${zoneRes.body}`);
    return;
  }
  console.log(`구역 생성 성공 (100석)`);

  // ── 6. 회차 상태 ON_SALE 변경 ─────────────────────────────
  const statusRes = http.patch(
    `${ADMIN_URL}/admin/performances/shows/${showId}/status?status=ON_SALE`,
    null,
    { headers: authHeader(adminToken) }
  );
  console.log(`회차 상태 변경: ${statusRes.status}`);

  // ── 7. 좌석 목록 조회 ─────────────────────────────────────
  const seatsRes = http.get(`${BASE_URL}/api/shows/${showId}/seats`,
    { headers: authHeader(userToken) });
  const seats = parse(seatsRes, '좌석 조회');
  const seatIds = seats?.data?.map(s => s.id) || [];
  console.log(`좌석 수=${seatIds.length} 첫번째 seatId=${seatIds[0]}`);

  if (seatIds.length === 0) {
    console.error('좌석 조회 실패 — 구역/좌석 생성이 제대로 됐는지 확인하세요');
    return;
  }

  // ── 8. 테스트 유저 1000명 생성 ─────────────────────────────
  const tokens = [];
  for (let i = 0; i < 1000; i++) {
    const email = `test_user_${i}@stagepass.test`;

    http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({
      email, password: 'Test1234!', name: `테스트유저${i}`, phone: `010-1111-${String(i).padStart(4, '0')}`,
    }), { headers: JSON_HEADERS });

    const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
      email, password: 'Test1234!',
    }), { headers: JSON_HEADERS });

    const login = parse(loginRes, `유저${i} 로그인`);
    const token = login?.data?.accessToken;
    if (token) {
      tokens.push(token);
    } else {
      console.error(`유저${i} 로그인 실패`);
    }
  }
  console.log(`유저 토큰 수집 완료: ${tokens.length}개 (목표: 1000개)`);

  // ── 결과 출력 ─────────────────────────────────────────────
  const result = { showId, seatIds, tokens };
  console.log('\n====== 아래 내용을 k6/data/test-data.json 으로 저장하세요 ======');
  console.log(JSON.stringify(result, null, 2));
  console.log('================================================================');

  console.log(`\n✅ 테스트 준비 완료. 다음 명령을 실행하세요:`);
  console.log(`k6 run k6/seat-concurrency-test.js -e SHOW_ID=${showId} -e SEAT_ID=${seatIds[0]}`);
  console.log(`k6 run k6/queue-concurrency-test.js -e SHOW_ID=${showId}`);
}
