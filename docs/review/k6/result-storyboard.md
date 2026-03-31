# GET /api/people/{publicId}/storyboard/projects 성능 테스트 결과

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 테스트 일시 | 2026-03-31 |
| API 서버 | EC2 t2.micro (13.125.228.215:8080) |
| DB | RDS MySQL t3.micro |
| k6 서버 | EC2 t2.micro |
| 더미 데이터 | storyboard_project 20개 × scene 10개 = 총 200개 |

## 부하 설정

```js
stages: [
  { duration: '30s', target: 10 },  // 워밍업
  { duration: '1m',  target: 50 },  // 부하 증가
  { duration: '2m',  target: 50 },  // 유지
  { duration: '30s', target: 0  },  // 종료
]
```

## 결과 (fetch join 적용 후)

| 지표 | 결과 | 기준 |
|---|---|---|
| p(95) | **28ms** | < 500ms ✅ |
| p(99) | 45ms | - |
| p(90) | 24ms | - |
| avg | 19ms | - |
| max | 274ms | - |
| 에러율 | **0.00%** | < 1% ✅ |
| TPS | **35.52/s** | - |
| 총 요청 수 | **8,500건** | - |
| 성공률 | **100%** | - |

## 쿼리 발생 현황

fetch join 적용으로 요청 1건당 **쿼리 1회** 발생

```sql
select distinct p1_0.id, ...
from person p1_0
left join storyboard_project sp1_0 on p1_0.id = sp1_0.person_id
left join storyboard_scene s1_0 on sp1_0.id = s1_0.project_id
where p1_0.public_id = '...'
```

> fetch join 미적용 시 예상 쿼리 수: 1 + 20 + 200 = **221회**

## 결과 (fetch join 제거 후 - N+1 발생)

| 지표 | 결과 |
|---|---|
| p(95) | **55.02ms** |
| p(99) | - |
| p(90) | 44.56ms |
| avg | 35.31ms |
| max | 221.39ms |
| 에러율 | **0.00%** |
| TPS | **34.97/s** |
| 총 요청 수 | **8,411건** |
| data_received | 18 MB |

## 최종 비교

| 지표 | fetch join 적용 | fetch join 제거 (N+1) |
|---|---|---|
| p(95) | **28ms** | 55.02ms (+96%) |
| p(90) | **24ms** | 44.56ms (+86%) |
| avg | **19ms** | 35.31ms (+86%) |
| max | 274ms | 221.39ms |
| TPS | 35.52/s | 34.97/s |
| 에러율 | 0.00% | 0.00% |
| data_received | 18.3 MB | 18 MB |
| 쿼리 수/요청 | **1회** | 22회 (1 + 1 + 20) |

## 결과 분석

**latency가 약 2배 증가** (p95: 28ms → 55ms)

N+1 버전의 실제 쿼리 발생 구조:
- person 조회: 1회
- storyboard_project 전체 조회: 1회
- storyboard_scene 조회: 프로젝트당 1회 × 20 = **20회**
- **총 22회** (예상 221회보다 적음 - project 컬렉션이 1번에 로드되기 때문)

**data_received가 비슷한 이유 (18.3MB vs 18MB)**
fetch join은 200행 카테시안 곱을 1번에, N+1은 22번 나눠서 전송하지만
총 데이터량은 동일해서 data_received는 유사하게 나옴.

## 결론

| | fetch join | N+1 |
|---|---|---|
| 쿼리 수 | 1회 | 22회 |
| p(95) latency | 28ms | 55ms |
| 성능 차이 | - | **약 2배 느림** |

더 극적인 차이를 보려면:
- 데이터 규모 확대 (project 100개 이상)
- scene에서 cards까지 접근하는 시나리오 (3단계 N+1)
