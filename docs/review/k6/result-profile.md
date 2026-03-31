# GET /api/people/{publicId} 성능 테스트 결과

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 테스트 일시 | 2026-03-31 |
| API 서버 | EC2 t2.micro (13.125.228.215:8080) |
| DB | RDS MySQL t3.micro |
| k6 서버 | EC2 t2.micro |
| 더미 데이터 | person 1명, sns 5개, gallery 30개, movie 20개, tag 10개 |

## 부하 설정

```js
stages: [
  { duration: '30s', target: 10 },  // 워밍업
  { duration: '1m',  target: 50 },  // 부하 증가
  { duration: '2m',  target: 50 },  // 유지
  { duration: '30s', target: 0  },  // 종료
]
```

## 결과

| 지표 | 결과 | 기준 |
|---|---|---|
| p(95) | **17.28ms** | < 500ms ✅ |
| p(90) | 14.33ms | - |
| avg | 10.55ms | - |
| max | 165.56ms | - |
| 에러율 | **0.00%** | < 1% ✅ |
| TPS | **35.8/s** | - |
| 총 요청 수 | **8,613건** | - |
| 성공률 | **100%** | - |

## 원문 출력

```
  █ THRESHOLDS

    http_req_duration
    ✓ 'p(95)<500' p(95)=17.28ms

    http_req_failed
    ✓ 'rate<0.01' rate=0.00%


  █ TOTAL RESULTS

    checks_total.......: 8613    35.839309/s
    checks_succeeded...: 100.00% 8613 out of 8613
    checks_failed......: 0.00%   0 out of 8613

    ✓ status is 200

    HTTP
    http_req_duration..............: avg=10.55ms min=6.28ms med=9.13ms max=165.56ms p(90)=14.33ms p(95)=17.28ms
      { expected_response:true }...: avg=10.55ms min=6.28ms med=9.13ms max=165.56ms p(90)=14.33ms p(95)=17.28ms
    http_req_failed................: 0.00%  0 out of 8613
    http_reqs......................: 8613   35.839309/s

    EXECUTION
    iteration_duration.............: avg=1.01s   min=1s     med=1s     max=1.16s    p(90)=1.01s   p(95)=1.01s
    iterations.....................: 8613   35.839309/s
    vus............................: 2      min=1         max=50
    vus_max........................: 50     min=50        max=50

    NETWORK
    data_received..................: 9.3 MB 39 kB/s
    data_sent......................: 947 kB 3.9 kB/s
```

## 비고

- `GET /api/people/{publicId}` 는 fetch join 미적용 API (`findByPublicId` 단순 조회)
- `snsList`, `profileTags`는 LAZY 로딩이지만 `ProfileResponse.from(person, ...)` 내부에서 접근 시 N+1 발생 가능
- 위 결과는 N+1 발생 여부와 무관하게 17ms로 쾌적한 수치를 보임
- 실제 N+1 효과가 극적으로 나타나는 API는 `GET /api/people/{publicId}/storyboard/projects` (project 20개 × scene 10개 = 221쿼리 vs 1쿼리)
