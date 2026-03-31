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

## 비교 (수정 전 테스트 예정)

| 지표 | fetch join 적용 후 | fetch join 제거 후 (예정) |
|---|---|---|
| p(95) | 28ms | - |
| TPS | 35.52/s | - |
| 쿼리 수/요청 | 1회 | 221회 (예상) |
| 에러율 | 0.00% | - |
