import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL   = 'http://13.125.228.215:8080';
const PUBLIC_ID  = '9d981c8b-7a5f-4f3c-99a9-c72f776f1253';
const EMAIL      = 'test@test.com';   // prod RDS에 등록된 테스트 계정
const PASSWORD   = 'qwer1234';         // 위 계정의 비밀번호

export const options = {
  stages: [
    { duration: '30s', target: 10 },  // 워밍업
    { duration: '1m',  target: 50 },  // 부하 증가
    { duration: '2m',  target: 50 },  // 유지
    { duration: '30s', target: 0  },  // 종료
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed:   ['rate<0.01'],
  },
};

// 테스트 시작 전 1회 실행 - 로그인해서 access token 획득
export function setup() {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, { 'login success': (r) => r.status === 200 });

  // res.cookies로 access_token 추출 (Set-Cookie 헤더가 여러 개일 때 안전)
  const token = res.cookies['access_token'] ? res.cookies['access_token'][0].value : null;

  if (!token) {
    console.error('access_token을 가져오지 못했습니다. 계정 정보를 확인하세요.');
    console.log('login status:', res.status);
    console.log('cookies:', JSON.stringify(res.cookies));
  }

  return { token };
}

export default function (data) {
  const res = http.get(
    `${BASE_URL}/api/people/${PUBLIC_ID}/storyboard/projects`,
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
      },
    }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  sleep(1);
}
