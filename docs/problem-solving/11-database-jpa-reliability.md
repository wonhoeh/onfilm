# Flyway와 실제 MySQL 검증으로 DB·JPA 신뢰성 확보

- 작업일: 2026-09-01
- 문서 작성일: 2026-09-01
- 관련 커밋: 첫 커밋 `7caacc1`, 마지막 커밋 `4b944d7`을 포함한 DB 신뢰성 작업 16개
- 상태: API DB 적용·검증 완료, Worker DB 적용은 후속 과제

## 문제

JPA 엔티티 리팩토링으로 객체의 생성과 연관관계 책임은 정리됐지만 실제 DB Schema와 동시성까지 같은 규칙을 지킨다는 근거가 부족했다.

- Hibernate `ddl-auto`가 실행 환경에 따라 Schema를 생성·변경했다.
- Schema 변경 이력과 배포 순서를 저장소에서 확인할 수 없었다.
- API와 Worker가 같은 MySQL을 사용할 때 테이블과 변경 권한의 소유자가 불명확했다.
- H2 테스트가 통과해도 MySQL의 enum, Collation, FK, CHECK, Transaction과 Lock이 같다고 보장할 수 없었다.
- 서비스의 사전 중복 검사 뒤 동시에 INSERT하면 둘 다 검사를 통과할 수 있었다.
- JPA cascade와 `orphanRemoval`은 테스트했지만 DB FK의 `ON DELETE` 정책과 일치하는지 전수 확인하지 않았다.
- Nullable과 `@OrderColumn`을 도메인 의미가 아니라 annotation만 보고 강화하면 정상 Aggregate 저장을 깨뜨릴 수 있었다.
- Index는 실제 SQL과 데이터 분포보다 Repository 이름을 보고 추측해서 추가할 위험이 있었다.

운영 데이터는 아직 없었지만, 서비스가 운영된 뒤 이 문제를 해결하면 기존 Schema와 데이터를 변환해야 하므로 빈 DB에서 변경할 수 있는 시점에 기준을 세울 필요가 있었다.

## 원인

### Schema 생성과 검증 책임의 혼재

Hibernate가 개발 편의를 위한 Mapping 도구인 동시에 Schema 변경 도구 역할까지 맡았다. Entity annotation은 현재 애플리케이션이 기대하는 구조는 보여주지만 변경 순서, 데이터 변환과 이미 적용된 변경의 불변 이력을 표현하지 못한다.

### 테스트 DB와 목표 DB의 차이

H2는 빠른 회귀 테스트에 유용하지만 MySQL InnoDB의 잠금 대기, enum DDL, Collation 기반 비교, CHECK·FK 이름과 실행 계획을 그대로 재현하지 않는다. 테스트 성공 범위와 운영 신뢰성 근거를 구분하지 않았다.

### 계층별 무결성 책임의 오해

서비스 사전 검사는 구체적인 오류를 빠르게 반환하지만 경쟁 조건을 직렬화하지 않는다. 반대로 모든 규칙을 DB에 강제하면 `@OrderColumn`의 중간 INSERT·UPDATE처럼 ORM이 정상적으로 동작하기 위해 필요한 과도 상태까지 거부할 수 있다.

### 측정 없는 Index 결정

복합 Index는 읽기를 줄일 수 있지만 모든 INSERT와 상태 변경 때 갱신되고 디스크를 사용한다. 실제 WHERE·ORDER BY와 읽은 행 수를 확인하지 않으면 사용되지 않는 Index를 운영 비용으로 남길 수 있다.

## 해결

### 1. DB 소유권과 변경 규칙 확정

하나의 MySQL 서버를 공유하되 API와 Worker의 논리 DB와 계정을 분리했다.

```text
MySQL
├── onfilm_api / onfilm_api_app
└── onfilm_worker / onfilm_worker_app
```

API 저장소는 `onfilm_api`만, Worker 저장소는 `onfilm_worker`만 소유한다. DB 간 FK·JOIN·직접 Repository와 분산 트랜잭션을 금지하고 Kafka 메시지와 HMAC Callback을 서비스 경계로 유지했다. 로컬 초기화 SQL과 검증 스크립트로 상대 DB 접근이 거부되는 것도 확인할 수 있게 했다.

저장소의 `AGENTS.md`에는 Entity와 Migration 동시 변경, 적용된 Migration 수정 금지, MySQL Testcontainers와 Hibernate validate 완료 조건, Index 전후 EXPLAIN 의무를 추가했다.

### 2. Flyway를 Schema 단일 정책원으로 전환

보존할 운영 데이터가 없다는 조건을 활용해 기존 Schema를 baseline 등록하지 않고 빈 DB용 `V1__create_initial_schema.sql`을 작성했다. API 소유 19개 테이블, 명시적인 PK·FK·UNIQUE·Index와 삭제 정책을 포함했다.

이후 변경은 순서가 증가하는 Migration으로 분리했다.

| Migration | 역할 |
|---|---|
| V1 | API 초기 Schema 19개 테이블 생성 |
| V2 | 표준 장르 19개 Reference Data 저장 |
| V3 | Refresh Token hash Collation과 User FK 강화 |
| V4 | Aggregate CHECK·UNIQUE와 값 범위 강화 |
| V5 | 근거가 확인된 미디어 유지보수 Composite Index 추가 |

MySQL 환경의 Hibernate는 `ddl-auto: validate`만 수행한다. 애플리케이션 시작 시 Flyway 적용 또는 Mapping 검증이 실패하면 서비스도 시작하지 않는다.

### 3. 운영 Reference Data와 Fixture 분리

기존 `data.sql`에 섞여 있던 표준 장르, 개발 계정과 예제 데이터를 분리했다.

- 모든 환경에 필요한 표준 장르: Flyway V2
- 로컬 화면 확인용 데이터: `dev` 프로필 전용 Initializer
- 테스트 데이터: 각 테스트가 독립적으로 생성
- 대량 성능 데이터: 전용 benchmark SQL

테스트가 로컬 DB 상태와 고정 예제 데이터에 의존하지 않고, 개발 Fixture가 운영에 유입되지 않도록 했다.

### 4. MySQL Testcontainers와 CI 구축

`mysql:8.4.11`을 고정한 공통 Testcontainers 환경을 만들고 다음 항목을 자동으로 검증했다.

- 빈 `onfilm_api`에 V1부터 최신 Migration 적용
- Hibernate `validate` 통과
- 논리 DB, 전용 계정, 문자 집합과 Collation
- Repository 저장·조회·삭제와 정렬
- JPA cascade, orphanRemoval과 DB FK 삭제 결과
- Constraint 거부, Transaction, Lock과 동시성

H2 기반 단위 테스트와 MySQL 통합 테스트를 CI의 독립 작업으로 실행하고, Docker에 접근할 수 없으면 MySQL 검증을 건너뛰지 않고 실패하도록 했다.

### 5. Constraint 전수 감사와 강화

API 소유 19개 테이블의 UNIQUE, Nullable과 FK를 JPA Mapping·V1 Schema·도메인 생명주기와 대조했다.

주요 결정은 다음과 같다.

- `refresh_tokens.token_hash`: `ascii_bin`으로 Java와 DB의 정확 비교 의미 일치
- `refresh_tokens.user_id`: User FK와 `ON DELETE CASCADE`로 고아 인증 세션 차단
- Gallery image key: 부모 안의 중복을 DB UNIQUE로 차단
- Movie runtime·release year와 순서 컬럼: CHECK로 허용 범위 보호
- Movie·Person ID만 저장하는 미디어 작업 이력: 삭제 이후에도 추적해야 하므로 약한 관계 유지
- JPA `@OrderColumn` 컬럼: Hibernate의 INSERT 후 UPDATE 절차를 위해 nullable 유지, 음수 CHECK와 commit 후 순서 테스트로 보완
- `(parent_id, sort_order)` UNIQUE: 재정렬 중간 UPDATE 충돌 때문에 적용하지 않음

DB Constraint를 무조건 강하게 만드는 대신 ORM 저장 절차와 도메인 의미를 함께 확인했다.

### 6. 실제 MySQL Transaction과 동시성 검증

두 스레드와 독립 트랜잭션으로 다음 경쟁을 재현했다.

- 같은 이메일·사용자명 사전 조회를 둘 다 통과한 뒤 동시 INSERT
- 같은 Refresh Token의 동시 소비
- 같은 Media Job의 `DONE`·`FAILED` 동시 전이
- 같은 UploadRequest의 비관적 잠금 대기
- 두 Publisher의 동일 Outbox 동시 선점

동시 회원가입은 MySQL UNIQUE가 하나만 commit하고 나머지를 실제 제약 이름으로 거부했다. Refresh Token과 Media Job은 `@Version`을 사용해 한 상태만 commit하고 충돌 요청을 도메인 오류로 변환했다. Outbox 잠금은 Kafka 발행까지 유지하지 않고 짧은 DB 선점 트랜잭션에서 끝냈다.

### 7. EXPLAIN 기반 Composite Index 적용

Person 1,000건, Movie 관계 각 10만 건, Job·Outbox 각 20만 건을 전용 MySQL에 적재하고 주요 SQL 6개의 `EXPLAIN ANALYZE` 기준선을 기록했다.

네 후보 중 실제 실행 계획이 개선된 세 개만 V5에 적용했다.

| Index | 지원 쿼리 | 컬럼 순서 근거 |
|---|---|---|
| `(status, completed_at)` | 완료 Job 정리 | 상태 동등·IN 조건 뒤 시간 범위 |
| `(status, published_at)` | 발행 완료 Outbox 정리 | 상태 동등 조건 뒤 시간 범위 |
| `(status, created_at)` | oldest PENDING | 상태 범위 안에서 created time 정렬·MIN |

`(status, lease_until)`는 현재 OR 쿼리에서 선택되지 않아 제외했다. 같은 컨테이너에서 Index를 `INVISIBLE`·`VISIBLE`로 전환해 데이터와 캐시 조건을 맞춘 결과는 다음과 같았다.

| 쿼리 | 적용 전 중앙값 | 적용 후 중앙값 | 실행 계획 변화 |
|---|---:|---:|---|
| 완료 Job 정리 | 123ms | 3.40ms | 20만 행 scan → 7,056건 range scan |
| 발행 완료 Outbox 정리 | 1,357ms | 3.57ms | 16만 건 lookup → 8,064건 range scan |
| oldest PENDING | 153ms | 약 0.000334ms | 2만 건 집계 → 실행 전 최솟값 결정 |

신규 Index 저장 공간은 20만 행 기준 합계 34.72MiB였다. 조회 개선만 기록하지 않고 쓰기 갱신과 저장 공간 비용도 함께 남겼다.

## 기술 선택과 트레이드오프

### 선택한 방법

#### Flyway SQL과 Hibernate validate

Schema 변경 이력과 적용 순서를 명시적인 SQL로 관리하고 Hibernate는 Mapping 불일치만 탐지하게 했다. 엔티티와 SQL을 함께 관리해야 하는 비용 대신 재현 가능한 배포와 리뷰 가능한 변경 이력을 얻었다.

#### H2와 MySQL Testcontainers의 역할 분리

H2는 빠른 테스트에 유지하고 DB 의미가 중요한 검증은 실제 MySQL에서 실행했다. CI 시간과 Docker 의존성이 늘지만 운영 DB 차이로 인한 오류를 병합 전에 찾을 수 있다.

#### 논리 DB·계정 분리

초기 비용을 고려해 물리 MySQL은 공유하되 서비스별 Schema와 권한을 분리했다. 자원과 장애 영역은 공유하지만 소유권과 잘못된 접근의 영향 범위를 제한하고 나중에 물리 분리할 경계를 확보했다.

#### 애플리케이션 검증과 DB Constraint의 계층화

사전 검사는 사용자 친화적인 오류를, DB는 경쟁 조건의 최종 무결성을 담당하게 했다. 같은 규칙이 일부 겹치지만 실패 시점과 보호 범위가 다르다.

#### 실행 계획이 개선된 Index만 적용

후보를 모두 추가하지 않고 실제 읽은 행이 줄어든 세 개만 선택했다. 측정용 데이터와 실험 절차를 유지해야 하지만 사용되지 않는 Index의 지속적인 쓰기 비용을 피했다.

### 검토한 대안

#### Hibernate `ddl-auto: update` 유지

SQL 작성 비용은 적지만 변경 이력, 배포 순서와 예상 DDL을 검토하기 어렵고 두 애플리케이션의 소유권도 흐려져 선택하지 않았다.

#### H2 테스트만 유지

가장 빠르지만 MySQL Collation, FK·CHECK, InnoDB Lock과 실행 계획을 증명할 수 없다. 빠른 피드백에는 H2를 남기되 MySQL 검증을 최종 기준으로 추가했다.

#### API와 Worker용 물리 MySQL 즉시 분리

장애와 자원 경합까지 분리할 수 있지만 1~2명 MVP에는 운영 비용이 크다. 논리 DB와 계정 분리로 시작하고 부하·가용성 요구가 생기면 물리 분리한다.

#### 모든 관계·순서에 강한 FK·UNIQUE 적용

정적으로는 안전해 보이지만 이력 보존용 약한 관계와 Hibernate 재정렬의 중간 상태를 깨뜨릴 수 있다. Aggregate별 생명주기와 실제 SQL을 기준으로 선택했다.

#### 후보 Index 네 개 일괄 적용

구현은 단순하지만 사용되지 않는 lease Index까지 모든 Outbox 쓰기에 비용을 추가한다. OR Optimizer가 선택하지 않는 것을 측정하고 제외했다.

### 감수한 비용

- Schema 변경마다 Entity와 SQL Migration을 함께 관리해야 한다.
- 개발자와 CI에서 Docker Engine이 필요하고 통합 테스트 시간이 늘어난다.
- API와 Worker의 DB·계정·Flyway 이력을 각각 관리해야 한다.
- DB 제약 위반을 안정적인 Domain Error로 변환하는 코드가 필요하다.
- 실제 동시성 테스트는 순서 제어와 timeout 때문에 단위 테스트보다 복잡하다.
- Composite Index 세 개가 benchmark 20만 행에서 34.72MiB를 추가로 사용하고 쓰기마다 갱신된다.
- H2와 MySQL 테스트가 공존하므로 각 테스트가 무엇을 증명하는지 구분해야 한다.

## 검증

V5 적용 작업에서 `./gradlew check --rerun-tasks --no-daemon`을 실행해 다음 결과를 확인했다.

| 구분 | 테스트 수 | 결과 |
|---|---:|---|
| 단위·빠른 테스트 | 299 | 성공 |
| MySQL 통합 테스트 | 43 | 성공 |
| 합계 | 342 | 실패 0 |

주요 검증 범위는 다음과 같다.

- 빈 MySQL에 V1~V5 적용과 `flyway_schema_history` 최신 버전 확인
- Hibernate `ddl-auto: validate` 통과
- 표준 장르 19개의 ID·정규화 값과 조회 확인
- Movie·Storyboard Aggregate의 저장 순서, orphanRemoval과 cascade 확인
- User·Refresh Token의 UNIQUE, NOT NULL, FK와 binary hash 비교 확인
- 순서·Movie 범위 CHECK와 Gallery 중복 거부 확인
- Job·Outbox commit·rollback과 `REQUIRES_NEW` 독립 commit 확인
- 비관적 잠금 대기, Outbox 중복 선점 방지 확인
- 동시 INSERT UNIQUE 충돌과 낙관적 락 충돌 확인
- V5 Index의 존재와 복합 컬럼 순서 확인
- 동일 MySQL·데이터·SQL의 Index 전후 `EXPLAIN ANALYZE` 측정
- `git diff --check` 통과

성능 수치는 전용 로컬 benchmark 환경의 상대 비교이며 운영 SLA로 해석하지 않는다. 쓰기 처리량, lock wait 시간과 운영 데이터 기반 Index 선택성은 아직 측정하지 않았다.

## 결과

| 변경 전 | 변경 후 |
|---|---|
| Hibernate가 환경별로 Schema 생성·변경 | Flyway V1~V5가 변경 이력 관리, Hibernate는 validate |
| API·Worker가 같은 DB를 사용할 때 소유권 불명확 | 논리 DB·계정·Migration 이력 분리와 교차 접근 금지 |
| H2 성공을 DB 신뢰성 근거로 사용 | MySQL 8.4.11 Testcontainers와 CI가 최종 영속 검증 담당 |
| 운영 기준 데이터와 개발 예제가 `data.sql`에 혼재 | Reference Data, 개발 Fixture와 테스트 Fixture 분리 |
| 사전 중복 검사에 의존 | DB UNIQUE와 실제 동시 INSERT 테스트로 경쟁 조건 방어 |
| Cascade·Nullable·FK 정책을 부분적으로 확인 | API 19개 테이블 Constraint 전수 감사와 V3·V4 강화 |
| Lock annotation 존재만 확인 | 두 스레드·독립 트랜잭션으로 commit 결과 검증 |
| 추측으로 Index 후보 선정 | 20만 행 EXPLAIN 기준선, V5 적용과 paired 전후 비교 |

이번 작업으로 Entity Mapping, SQL Schema, DB Constraint, Transaction·Lock, CI와 성능 근거가 하나의 변경 흐름으로 연결됐다. 새 Entity나 컬럼을 추가할 때 무엇을 함께 수정하고 어떤 테스트로 완료를 판단할지도 [DB·JPA 신뢰성 컨벤션](../convention/database-jpa-reliability-convention.md)에 남겼다.

## 후속 과제

- Worker 저장소에 `onfilm_worker` Flyway V1, Hibernate validate와 MySQL Testcontainers를 적용한다.
- 공개 운영 전 DDL Migration 계정과 DML Runtime 계정을 분리한다.
- DB deadlock과 lock wait timeout 재시도 정책을 정하고 실제 MySQL에서 검증한다.
- Index 추가에 따른 Job·Outbox 쓰기 처리량과 페이지 분할 비용을 측정한다.
- 실제 운영 데이터와 slow query log로 Index 선택성과 Alert 기준을 재감사한다.
- Q4 Outbox claim이 병목으로 확인되면 PENDING 발행과 PUBLISHING lease 복구 쿼리 분리를 비교한다.
- 운영 데이터가 생긴 이후에는 백업·복구·무중단 Migration과 rollback 전략을 별도 설계한다.
- MySQL 물리 자원 경합이 확인되면 API와 Worker의 인스턴스 분리를 검토한다.

## 포트폴리오 요약 후보

Hibernate 자동 Schema 생성과 H2 테스트만으로는 MySQL의 Constraint·Transaction·Lock을 보장할 수 없는 문제를 발견하고, Flyway V1~V5와 Hibernate validate, MySQL 8.4.11 Testcontainers 기반 CI로 DB 변경 흐름을 재설계했습니다. 19개 테이블의 무결성과 동시 요청을 43개 MySQL 통합 테스트로 검증하고, Job·Outbox 각 20만 건의 EXPLAIN 분석을 통해 완료 Job 정리 97.2%, 발행 완료 Outbox 정리 99.7%의 실행 시간 감소를 확인하면서 효과 없는 Index는 제외했습니다.
