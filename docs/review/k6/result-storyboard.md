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

---

## 1차 테스트 - fetch join 제거 (N+1 발생)

| 지표 | 결과 | 기준 |
|---|---|---|
| p(95) | **55.02ms** | < 500ms ✅ |
| p(90) | 44.56ms | - |
| avg | 35.31ms | - |
| max | 221.39ms | - |
| 에러율 | **0.00%** | < 1% ✅ |
| TPS | **34.97/s** | - |
| 총 요청 수 | **8,411건** | - |
| 성공률 | **100%** | - |

### 쿼리 발생 현황

요청 1건당 **쿼리 24회** 발생

```
1.  SELECT ... FROM person WHERE public_id = '...'          ← isOwner 체크
2.  SELECT ... FROM users WHERE person_id = 1               ← isOwner 체크
3.  SELECT ... FROM person WHERE public_id = '...'          ← person 조회
4.  SELECT ... FROM storyboard_project WHERE person_id = 1  ← 프로젝트 목록
5.  SELECT ... FROM storyboard_scene WHERE project_id = 1   ← 씬 (project 1)
6.  SELECT ... FROM storyboard_scene WHERE project_id = 2   ← 씬 (project 2)
    ...
24. SELECT ... FROM storyboard_scene WHERE project_id = 20  ← 씬 (project 20)
```

---

## 2차 테스트 - fetch join 적용

| 지표 | 결과 | 기준 |
|---|---|---|
| p(95) | **32.7ms** | < 500ms ✅ |
| p(90) | 28.16ms | - |
| avg | 21.1ms | - |
| max | 377.7ms | - |
| 에러율 | **0.00%** | < 1% ✅ |
| TPS | **35.40/s** | - |
| 총 요청 수 | **8,527건** | - |
| 성공률 | **100%** | - |

### 쿼리 발생 현황

요청 1건당 **쿼리 3회** 발생

```
1. SELECT ... FROM person WHERE public_id = '...'           ← isOwner 체크
2. SELECT ... FROM users WHERE person_id = 1                ← isOwner 체크
3. SELECT distinct ... FROM person
   LEFT JOIN storyboard_project ON person.id = project.person_id
   LEFT JOIN storyboard_scene   ON project.id = scene.project_id
   WHERE person.public_id = '...'                           ← fetch join 1회
```

---

## 최종 비교

| 지표 | N+1 (fetch join 제거) | fetch join 적용 |
|---|---|---|
| p(95) | 55.02ms | **32.7ms** (-41%) |
| p(90) | 44.56ms | **28.16ms** (-37%) |
| avg | 35.31ms | **21.1ms** (-40%) |
| max | 221.39ms | 377.7ms |
| TPS | 34.97/s | 35.40/s |
| 에러율 | 0.00% | 0.00% |
| 쿼리 수/요청 | 24회 | **3회** (-87%) |

## 결과 분석

**쿼리 수 87% 감소, p95 latency 40% 개선**

N+1 버전의 실제 쿼리 발생 구조:
- person 조회: 2회 (isOwner 체크 1회 + storyboard 조회 1회)
- storyboard_project 전체 조회: 1회
- storyboard_scene 조회: 프로젝트당 1회 × 20 = **20회**
- user 조회: 1회
- **총 24회**

**data_received가 비슷한 이유**
fetch join은 200행 카테시안 곱을 1번에, N+1은 24번 나눠서 전송하지만
총 데이터량은 동일해서 data_received는 유사하게 나옴.

**TPS 차이가 미미한 이유**
sleep(1)로 VU당 1초에 1요청으로 제한되어 있어 쿼리 수와 무관하게 TPS 상한이 50으로 고정됨.
RDS 버퍼 풀에 데이터가 캐싱되어 24회 쿼리도 디스크 I/O 없이 처리됨.
