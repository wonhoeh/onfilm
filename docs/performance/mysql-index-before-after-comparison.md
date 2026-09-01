# 주요 SQL Index 적용 전후 비교

- 측정일: 2026-09-01
- 대상: OnFilm API DB
- DB: MySQL `8.4.11`, InnoDB
- Schema: Flyway V1~V5
- 비교 대상: `V5__add_media_maintenance_indexes.sql`

## 결론

미디어 유지보수 쿼리의 조건과 일치하는 Composite Index 세 개를 적용해 불필요한 행 탐색을 줄였다.

| 쿼리 | 적용 전 중앙값 | 적용 후 중앙값 | 변화 | 핵심 실행 계획 변화 |
|---|---:|---:|---:|---|
| Q3 완료 Job 정리 | 123ms | 3.40ms | 97.2% 감소 | 20만 행 table scan → 7,056건 covering range scan |
| Q5 발행 완료 Outbox 정리 | 1,357ms | 3.57ms | 99.7% 감소 | PUBLISHED 16만 건 조회 → 8,064건 covering range scan |
| Q6 oldest PENDING | 153ms | 약 0.000334ms | 실행 전 결정 | PENDING 2만 건 집계 → Min/Max optimization |

Q1 필모그래피와 Q2 Job timeout은 V5 대상이 아니며 기존 Index 접근 경로를 유지했다. Q4 Outbox claim도 새 Index를 사용하지 않고 기존 `(status, next_attempt_at)` 경로에서 11,006행을 읽었으므로 이번 변경의 개선 대상으로 주장하지 않는다.

## 비교 방법

[적용 전 기준 데이터](mysql-index-baseline.md)와 동일하게 다음 조건을 사용했다.

- Person 1,000건
- Movie·MoviePerson·MoviePersonRole 각 100,000건
- MediaEncodeJob·MediaEncodeOutbox 각 200,000건
- 같은 cutoff인 `2026-01-08 00:00:00`
- `ANALYZE TABLE` 실행
- 워밍업 1회 후 각 쿼리 3회 측정
- 세 실행 시간의 중앙값 사용

컨테이너 간 파일 시스템 캐시와 로컬 부하 차이를 결과로 오인하지 않도록 하나의 전용 MySQL 컨테이너에서 비교했다. V1~V5와 [동일한 benchmark 데이터](mysql-index-baseline-setup.sql)를 적용한 뒤 V5 Index를 `INVISIBLE`로 전환한 상태를 적용 전, 다시 `VISIBLE`로 전환한 상태를 적용 후로 측정했다.

`INVISIBLE` Index도 저장과 갱신은 계속되지만 Optimizer의 실행 계획 후보에서는 제외된다. 따라서 이 실험은 같은 데이터·버퍼 조건에서 조회 경로 차이를 비교하며, Index 추가에 따른 INSERT·UPDATE 비용을 측정하는 쓰기 성능 실험은 아니다.

## 상세 결과

### Q3: 완료 Job 정리

```sql
SELECT j.id
FROM media_encode_jobs j
WHERE j.status IN ('DONE', 'FAILED')
  AND j.completed_at < '2026-01-08 00:00:00';
```

| 구분 | 접근 경로 | 읽은 행 → 결과 행 | 실행 시간 3회 | 중앙값 |
|---|---|---:|---:|---:|
| 적용 전 | `media_encode_jobs` table scan | 200,000 → 7,056 | 198 / 84.3 / 123ms | 123ms |
| 적용 후 | `idx_media_encode_job_status_completed` covering range scan | 7,056 → 7,056 | 3.49 / 3.32 / 3.40ms | 3.40ms |

`status`의 동등·IN 조건을 첫 컬럼에 두고 `completed_at` 시간 범위를 두 번째 컬럼에 둔 결과, terminal 상태별 cutoff 범위를 Index에서 바로 탐색한다. 읽은 행은 96.5%, 중앙 실행 시간은 97.2% 줄었다.

### Q5: 발행 완료 Outbox 정리

```sql
SELECT o.id
FROM media_encode_outbox o
WHERE o.status = 'PUBLISHED'
  AND o.published_at < '2026-01-08 00:00:00';
```

| 구분 | 접근 경로 | 읽은 행 → 결과 행 | 실행 시간 3회 | 중앙값 |
|---|---|---:|---:|---:|
| 적용 전 | `idx_media_outbox_dispatch`의 status lookup 후 filter | 160,000 → 8,064 | 1,391 / 1,357 / 1,208ms | 1,357ms |
| 적용 후 | `idx_media_outbox_status_published` covering range scan | 8,064 → 8,064 | 3.57 / 3.51 / 4.31ms | 3.57ms |

PUBLISHED 상태 전체를 읽은 후 `published_at`을 검사하던 방식에서 필요한 시간 범위만 읽도록 변경됐다. 읽은 행은 95.0%, 중앙 실행 시간은 99.7% 줄었다.

### Q6: oldest PENDING 메트릭

```sql
SELECT MIN(o.created_at)
FROM media_encode_outbox o
WHERE o.status = 'PENDING';
```

| 구분 | 접근 경로 | 읽은 행 | 실행 시간 3회 | 중앙값 |
|---|---|---:|---:|---:|
| 적용 전 | status lookup 후 `MIN(created_at)` 집계 | 20,000 | 136 / 153 / 168ms | 153ms |
| 적용 후 | `(status, created_at)`의 첫 값을 실행 전에 결정 | 1 | 0.000334 / 0.000250 / 0.000458ms | 약 0.000334ms |

`status = PENDING` 범위 안에서 `created_at`이 이미 정렬되어 있어 MySQL이 `Rows fetched before execution`으로 최솟값을 결정했다. 매우 작은 시간 값은 로컬 타이머 정밀도의 영향을 받으므로 배수 개선보다 2만 행 집계가 사라진 실행 계획 변화가 핵심 근거다.

## 변경하지 않은 SQL

| 쿼리 | V5 적용 후 확인 결과 | 판단 |
|---|---|---|
| Q1 필모그래피 | Person FK, Movie PK, Role UNIQUE lookup과 temporary dedup·sort 유지 | 정렬 입력이 100건이므로 추가 Index 근거 없음 |
| Q2 Job timeout | 기존 `(status, requested_at)` range scan과 LIMIT 유지 | 기존 Index가 쿼리를 지원하므로 유지 |
| Q4 Outbox claim | 기존 `(status, next_attempt_at)`를 사용해 11,006행을 읽고 1,509건 filter 후 top-N sort | `(status, lease_until)` 후보는 OR 쿼리에서 선택되지 않아 V5에서 제외 |

Q4가 운영 병목으로 확인되면 PENDING 발행 조회와 PUBLISHING lease 복구 조회를 분리한 뒤 각각의 Index 사용 여부와 잠금 순서를 다시 측정한다. 현재 수치만으로 Repository 쿼리를 복잡하게 만들지는 않는다.

## 비용과 트레이드오프

200,000행 benchmark에서 MySQL이 보고한 신규 Index 저장 공간은 다음과 같다.

| Index | 크기 |
|---|---:|
| `idx_media_encode_job_status_completed` | 11.56MiB |
| `idx_media_outbox_status_published` | 11.58MiB |
| `idx_media_outbox_status_created` | 11.58MiB |
| 합계 | 34.72MiB |

세 Index는 조회 대상 행을 크게 줄이지만 다음 비용이 있다.

- Job과 Outbox INSERT 시 Index entry 추가
- status, `completed_at`, `published_at` 변경 시 관련 Index 갱신
- 데이터 증가에 따른 메모리·디스크 사용량 증가
- 쓰기 트랜잭션의 추가 B-Tree 변경과 페이지 분할 가능성

현재는 주기적으로 실행되는 정리·메트릭 쿼리의 스캔 감소가 명확하고, 효과가 없었던 lease Index는 제외해 쓰기 비용을 제한했다. 실제 운영 데이터 분포가 달라지면 slow query log와 메트릭을 기준으로 다시 감사한다.

## 재현 절차

1. 전용 MySQL `8.4.11` 컨테이너의 빈 `onfilm_api` DB에 V1부터 V5까지 적용한다.
2. [benchmark 데이터 SQL](mysql-index-baseline-setup.sql)을 실행한다.
3. V5 Index 세 개를 `INVISIBLE`로 바꾸고 `ANALYZE TABLE` 후 Q3·Q5·Q6을 워밍업 1회, 측정 3회 실행한다.
4. 같은 Index를 `VISIBLE`로 되돌리고 같은 순서로 반복한다.
5. [측정 SQL](mysql-index-baseline-queries.sql)의 실행 계획과 중앙값을 비교한다.
6. 실험 컨테이너를 제거한다.

```sql
ALTER TABLE media_encode_jobs
    ALTER INDEX idx_media_encode_job_status_completed INVISIBLE;

ALTER TABLE media_encode_outbox
    ALTER INDEX idx_media_outbox_status_published INVISIBLE,
    ALTER INDEX idx_media_outbox_status_created INVISIBLE;

-- 적용 전 측정 후 VISIBLE로 복구한다.
ALTER TABLE media_encode_jobs
    ALTER INDEX idx_media_encode_job_status_completed VISIBLE;

ALTER TABLE media_encode_outbox
    ALTER INDEX idx_media_outbox_status_published VISIBLE,
    ALTER INDEX idx_media_outbox_status_created VISIBLE;
```

이 명령은 benchmark 전용 DB에서만 사용한다. 애플리케이션 DB의 Flyway Migration을 수정하거나 운영 Index를 임의로 숨기는 절차가 아니다.

## 관련 자료

- [Index 적용 전 EXPLAIN 기준선](mysql-index-baseline.md)
- [기준 데이터 SQL](mysql-index-baseline-setup.sql)
- [측정 SQL](mysql-index-baseline-queries.sql)
- [MySQL Testcontainers 통합 테스트](../testing/mysql-testcontainers.md)
