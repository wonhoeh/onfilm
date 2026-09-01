# 주요 SQL Index 적용 전 EXPLAIN 기준선

- 측정일: 2026-09-01
- 대상: OnFilm API DB
- DB: MySQL `8.4.11`, InnoDB
- Schema: Flyway V1~V4
- 상태: 현재 Index 기준선 기록 완료, 신규 Index 미적용

## 목적

Repository 이름만 보고 Index를 추측하지 않고 실제 SQL, 데이터 분포와 실행 계획을 기준으로 다음 질문에 답한다.

- 어떤 조회가 현재 Index를 제대로 사용하는가?
- 어떤 조회가 테이블 전체 또는 상태에 해당하는 많은 행을 읽는가?
- 정렬과 임시 테이블 비용은 실제 병목인가?
- 다음 Migration에서 추가할 Composite Index의 컬럼 순서는 무엇이어야 하는가?

이번 단계에서는 비교 기준을 보존하기 위해 신규 Index를 추가하지 않는다. 다음 단계에서 후보 Index를 적용한 뒤 같은 MySQL 버전·데이터·SQL로 다시 측정한다.

## 측정 데이터

[기준 데이터 SQL](mysql-index-baseline-setup.sql)은 빈 전용 benchmark DB에만 실행한다. 애플리케이션 Fixture나 운영 데이터로 사용하지 않는다.

| 테이블 | 행 수 | 분포 |
|---|---:|---|
| `person` | 1,000 | 균등한 식별자 |
| `movie` | 100,000 | MoviePerson과 1:1 연결 |
| `movie_person` | 100,000 | Person당 100개 참여, `sort_order` 0~99 |
| `movie_person_role` | 100,000 | 참여당 ACTOR 역할 1개 |
| `media_encode_jobs` | 200,000 | DONE 60%, FAILED 10%, REQUESTED 20%, PROCESSING 10% |
| `media_encode_outbox` | 200,000 | PUBLISHED 80%, PENDING 10%, PUBLISHING 5%, DEAD 5% |

`ANALYZE TABLE` 실행 후 동일 쿼리를 세 번 측정했다. 실행 시간은 세 결과의 중앙값이며 로컬 장비의 절대 성능이나 운영 SLA로 해석하지 않는다. Index 접근 경로, 실제 읽은 행 수와 상대적 차이가 핵심 근거다.

## 현재 관련 Index

| 테이블 | Index | 컬럼 순서 | 출처 |
|---|---|---|---|
| `movie_person` | `fk_movie_person_person` | `(person_id)` | MySQL이 FK를 위해 생성 |
| `movie_person` | `uk_movie_person_movie_id_person_id` | `(movie_id, person_id)` | 참여 중복 방지 UNIQUE |
| `movie_person_role` | `uk_movie_person_role_participation_role` | `(movie_person_id, role)` | 역할 중복 방지 UNIQUE |
| `media_encode_jobs` | `idx_media_encode_job_status_requested` | `(status, requested_at)` | timeout 조회 |
| `media_encode_jobs` | `idx_media_encode_job_user_status` | `(requested_by_user_id, status)` | 사용자별 상태 조회·집계 |
| `media_encode_outbox` | `idx_media_outbox_dispatch` | `(status, next_attempt_at)` | PENDING 발행 대상 조회 |

## 측정 SQL 선정

| 번호 | Repository 동작 | 선정 이유 |
|---|---|---|
| Q1 | `findFilmographyByPersonId` | 사용자 화면에서 Movie와 역할을 함께 조회하고 정렬 |
| Q2 | `findTop100ByStatusInAndRequestedAtBefore` | timeout 유지보수 작업의 반복 조회 |
| Q3 | `deleteTerminalBefore` 대상 탐색 | 완료 Job 보존 기간 정리 |
| Q4 | `findClaimable` | Outbox Publisher가 주기적으로 실행하는 핵심 조회 |
| Q5 | `deletePublishedBefore` 대상 탐색 | 발행 완료 Outbox 정리 |
| Q6 | `findOldestCreatedAtByStatus` | Outbox backlog age 메트릭 계산 |

DELETE 쿼리는 benchmark 데이터를 보존하기 위해 같은 WHERE 조건으로 ID를 조회했다. Q4의 `@Lock(PESSIMISTIC_WRITE)`가 생성하는 `FOR UPDATE`도 Index 접근 경로 비교에서는 제외하고, 잠금 대기 동작은 별도 MySQL 통합 테스트가 담당한다. 실행 SQL 전체는 [측정 쿼리](mysql-index-baseline-queries.sql)에 있다.

## EXPLAIN ANALYZE 결과

| SQL | 핵심 접근 경로 | 실제 읽은 행 → 결과 행 | 실행 시간 3회 | 중앙값 |
|---|---|---:|---:|---:|
| Q1 필모그래피 | `fk_movie_person_person` lookup + Movie PK + Role UNIQUE, temporary dedup, sort | 참여 100 + 연관 lookup 200 → 100 | 46.3 / 38.0 / 53.9ms | 46.3ms |
| Q2 Job timeout | `(status, requested_at)` index range scan, LIMIT | 100 → 100 | 4.94 / 4.88 / 6.01ms | 4.94ms |
| Q3 Job 정리 | `media_encode_jobs` table scan | 200,000 → 7,056 | 189 / 242 / 279ms | 242ms |
| Q4 Outbox claim | `(status, next_attempt_at)` index range, filter, top-N sort | 11,006 → 1,509 → 100 | 126 / 95.6 / 151ms | 126ms |
| Q5 Outbox 정리 | status만 Index lookup 후 `published_at` filter | 160,000 → 8,064 | 1,397 / 1,052 / 1,213ms | 1,213ms |
| Q6 oldest PENDING | status만 Index lookup 후 `MIN(created_at)` | 20,000 → 1 | 167 / 140 / 141ms | 141ms |

### Q1: 필모그래피

`person_id = 1` 조건은 MySQL이 FK용으로 생성한 `fk_movie_person_person(person_id)`를 사용해 전체 10만 행 중 참여 100건만 찾는다. Movie는 PK, Role은 `(movie_person_id, role)` UNIQUE로 단건 lookup한다.

`DISTINCT` Fetch Join 때문에 temporary deduplication과 `sort_order, id` 정렬이 발생하지만 정렬 입력은 100행이다. `(person_id, sort_order, id)`를 추가해도 Fetch Join의 중복 제거용 temporary table은 사라지지 않으며 현재 결과에서는 정렬 자체가 주된 비용이라는 근거가 없다. 따라서 이번 Index 후보에서 제외한다.

### Q2: Job timeout

상태를 먼저 좁히고 요청 시각 범위를 적용하는 `(status, requested_at)` 컬럼 순서가 쿼리와 일치한다. Index range scan 중 LIMIT 100에서 중단되어 중앙값 4.94ms였다. 기존 Index를 유지하고 중복 Index를 추가하지 않는다.

### Q3: 완료 Job 정리

조건은 `status IN (...)`과 `completed_at < cutoff`지만 현재 두 컬럼을 함께 지원하는 Index가 없다. Optimizer는 상태별 기존 Index보다 20만 행 table scan을 선택했고 7,056건을 찾기 위해 전체 행을 읽었다.

다음 후보는 `(status, completed_at)`이다. 상태의 동등·IN 조건을 앞에 두고 시간 범위를 뒤에 두면 terminal 상태별 보존 기간 범위를 Index에서 바로 탐색할 수 있다.

### Q4: Outbox claim

PENDING 분기는 기존 `(status, next_attempt_at)`를 사용하지만 PUBLISHING lease 복구 분기는 `status = PUBLISHING`까지만 Index에서 좁힌다. 그 결과 최종 후보 1,509건을 만들기 위해 11,006행을 읽고 `created_at` top-N 정렬을 수행했다.

다음 후보는 PUBLISHING 분기용 `(status, lease_until)`이다. OR 조건에서 두 Index를 함께 사용할지는 Optimizer 선택에 달려 있으므로 적용 후 반드시 같은 SQL로 재측정한다. 여전히 한쪽 Index만 사용한다면 PENDING 발행과 lease 복구 조회를 두 Repository 쿼리로 분리하는 방안도 비교한다.

### Q5: 발행 완료 Outbox 정리

현재 Index는 status까지만 사용할 수 있다. PUBLISHED 16만 행을 읽은 뒤 `published_at`으로 8,064행을 골라 중앙값 1,213ms가 걸렸다. `(status, published_at)`가 가장 우선순위가 높은 후보이며, 상태 동등 조건 다음에 시간 범위를 두는 순서가 쿼리와 일치한다.

### Q6: oldest PENDING 메트릭

PENDING 2만 행을 모두 읽어 애플리케이션 요청 시점의 가장 오래된 `created_at`을 집계한다. `(status, created_at)`를 사용하면 status별 첫 Index entry에서 MIN을 결정할 가능성이 있으므로 적용 후 `rows=1` 또는 Min/Max optimization 여부를 확인한다.

## 다음 단계 Index 후보

| 우선순위 | 후보 | 근거 | 적용 전 주의점 |
|---:|---|---|---|
| 1 | `media_encode_outbox(status, published_at)` | Q5가 16만 행을 읽고 1초 이상 소요 | Outbox 상태 변경의 Index 갱신 비용 |
| 2 | `media_encode_jobs(status, completed_at)` | Q3이 20만 행 table scan | Job 완료 시 Index 갱신 비용 |
| 3 | `media_encode_outbox(status, lease_until)` | Q4의 lease 분기가 status만 사용 | OR Optimizer가 새 Index를 선택하는지 확인 |
| 4 | `media_encode_outbox(status, created_at)` | Q6이 PENDING 2만 행 전체 집계 | Q4 ORDER BY에도 도움이 되는지 별도 확인 |

Index는 조회를 빠르게 하지만 INSERT와 status·시간 컬럼 UPDATE 비용, 디스크 사용량을 늘린다. 네 후보를 한꺼번에 정답으로 간주하지 않고 다음 단계에서 하나씩 적용한 실행 계획을 비교한다. 개선되지 않는 후보는 Migration에 넣지 않는다.

## 제외 범위

- PK·UNIQUE 단건 조회는 이미 읽는 행이 1건이므로 이번 측정에서 제외했다.
- Genre는 운영 기준 데이터가 19건이어서 현재 단계에서 Index 성능 실험의 우선순위가 낮다.
- Worker Inbox는 `onfilm_worker` DB와 Worker 저장소가 소유한다. API DB와 교차 조회·Index를 만들지 않으며 Worker 신뢰성 단계에서 별도로 측정한다.
- 시간은 로컬 Docker 환경의 값이다. 운영 배포 후에는 실제 분포와 slow query log로 후보를 다시 감사한다.

## 재현 절차

전용 임시 컨테이너에서만 실행한다.

```bash
docker run --name onfilm-index-baseline \
  -e MYSQL_ROOT_PASSWORD=onfilm_benchmark_password \
  -e MYSQL_DATABASE=onfilm_api \
  -d mysql:8.4.11
```

V1부터 V4까지 순서대로 적용한다.

```bash
for migration in \
  src/main/resources/db/migration/V1__create_initial_schema.sql \
  src/main/resources/db/migration/V2__seed_standard_genres.sql \
  src/main/resources/db/migration/V3__strengthen_refresh_token_constraints.sql \
  src/main/resources/db/migration/V4__strengthen_aggregate_constraints.sql
do
  docker exec -i onfilm-index-baseline \
    mysql -uroot -ponfilm_benchmark_password onfilm_api \
    < "$migration"
done
```

그다음 기준 데이터를 적재하고 실행 계획을 측정한다.

```bash
docker exec -i onfilm-index-baseline \
  mysql -uroot -ponfilm_benchmark_password \
  < docs/performance/mysql-index-baseline-setup.sql

docker exec -i onfilm-index-baseline \
  mysql -uroot -ponfilm_benchmark_password --table \
  < docs/performance/mysql-index-baseline-queries.sql
```

측정이 끝나면 임시 컨테이너를 제거한다.

```bash
docker rm -f onfilm-index-baseline
```

## 관련 문서

- [MySQL 트랜잭션과 잠금 통합 테스트](../testing/mysql-transaction-and-locking.md)
- [MySQL Constraint 감사](../review/database/mysql-constraint-audit.md)
- [Movie 참여·복수 역할 모델링 정책](../decisions/movie-person-role-modeling-policy.md)
