import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL  = 'http://13.125.228.215:8080';
const PUBLIC_ID = 'ce3ec2c5-ef98-4a04-83e5-fca8dabf5c26';
const EMAIL     = 'test@test.com';
const PASSWORD  = 'qwer1234';

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

export function setup() {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, { 'login success': (r) => r.status === 200 });

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
    `${BASE_URL}/api/people/${PUBLIC_ID}/movies`,
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
