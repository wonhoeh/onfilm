# k6 성능 테스트 계획서

## 1. 테스트 목적

N+1 쿼리 수정 전/후 API 성능 차이를 수치로 측정한다.

- **측정 지표**: TPS (Throughput), Latency (p95, p99), Error Rate
- **비교 방식**: fetch join 적용 코드(수정 후) vs fetch join 제거 코드(수정 전)

---

## 2. 테스트 환경

| 항목 | 스펙 |
|---|---|
| API 서버 | EC2 t2.micro (1 vCPU, 1GB RAM) |
| DB | RDS MySQL t3.micro |
| k6 서버 | EC2 t2.micro |
| 네트워크 | 같은 VPC, 프라이빗 IP 통신 |
| Spring Boot | 3.3.4 / Java 17 |

---

## 3. 더미 데이터

person 1명 기준으로 아래 데이터를 삽입한다.

| 테이블 | 수량 | 비고 |
|---|---|---|
| person | 1명 | publicId 고정 |
| storyboard_project | 20개 | person_id 연결 |
| storyboard_scene | 프로젝트당 10개 (총 200개) | project_id 연결 |
| person_gallery | 30개 | person_id 연결 |
| movie | 20개 | 필모그래피용 |
| movie_person | 20개 | person ↔ movie 연결 |
| person_sns | 5개 | person_id 연결 |
| profile_tag | 10개 | person_id 연결 |

> storyboard 조회는 project 20개 × scene 10개 구조에서 N+1이 가장 드라마틱하게 차이남

---

## 4. 테스트 대상 API

| API | 엔드포인트 | N+1 발생 여부 |
|---|---|---|
| 스토리보드 프로젝트 조회 | `GET /api/people/{publicId}/storyboard/projects` | ✅ 핵심 (fetch join 수정) |
| 갤러리 조회 | `GET /api/people/{publicId}/gallery` | ✅ |
| 필모그래피 조회 | `GET /api/people/{publicId}/movies` | ✅ |
| 프로필 조회 | `GET /api/people/{publicId}` | ✅ (snsList, profileTags) |

---

## 5. 시나리오

### 5-1. 부하 설정

```js
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // 워밍업
    { duration: '1m',  target: 50 },   // 부하 증가
    { duration: '2m',  target: 50 },   // 유지
    { duration: '30s', target: 0 },    // 종료
  ],
  thresholds: {
    http_req_duration: ['p95<500'],
    http_req_failed:   ['rate<0.01'],
  },
};
```

### 5-2. 테스트 흐름

```
1. 수정 후 코드 배포 (fetch join 적용 상태)
   → k6 실행 → 결과 저장

2. fetch join 제거 후 재배포 (N+1 발생 상태)
   → k6 실행 → 결과 저장

3. 수치 비교
```

---

## 6. 측정 항목

| 항목 | 설명 |
|---|---|
| `http_req_duration` | 전체 요청 응답 시간 |
| `p95` | 상위 5% 느린 요청의 응답 시간 |
| `p99` | 상위 1% 느린 요청의 응답 시간 |
| `http_reqs` | 초당 처리 요청 수 (TPS) |
| `http_req_failed` | 에러율 |

---

## 7. 비교 기준

| 항목 | N+1 수정 후 (목표) | N+1 수정 전 (예상) |
|---|---|---|
| 스토리보드 p95 | < 100ms | > 500ms |
| TPS | 높음 | 낮음 |
| DB 쿼리 횟수 | 1회 | 1 + N + N*M 회 |
| 에러율 | < 1% | connection pool 고갈 가능 |

> storyboard 기준: project 20개, scene 10개일 때 N+1은 최대 1 + 20 + 200 = 221회 쿼리 발생

---

## 8. 진행 순서

1. RDS에 더미 데이터 삽입 (SQL 스크립트)
2. API 서버 정상 응답 확인
3. **수정 후** k6 테스트 실행 및 결과 저장
4. fetch join 제거 → test 브랜치 재배포
5. **수정 전** k6 테스트 실행 및 결과 저장
6. 수치 비교 및 결과 문서화
