import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = 'http://13.125.228.215:8080'; // EC2 퍼블릭 IP
const PUBLIC_ID = 'k6-test-person-uuid-0001';

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

export default function () {
  const res = http.get(`${BASE_URL}/api/people/${PUBLIC_ID}`);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  sleep(1);
}
