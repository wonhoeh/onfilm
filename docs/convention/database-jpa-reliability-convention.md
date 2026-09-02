# OnFilm DB·JPA 신뢰성 컨벤션

- 제정일: 2026-09-01
- 적용 대상: OnFilm API·Encoding Worker의 JPA Entity, Repository, Flyway Migration, MySQL 통합 테스트
- Schema 기준: Flyway Versioned Migration

## 1. 목적

DB 변경을 엔티티 annotation이나 개발자의 로컬 상태에 의존하지 않고 다음 질문에 답할 수 있는 작업으로 만든다.

- 어떤 서비스가 이 테이블과 Migration을 소유하는가?
- 빈 MySQL에서 같은 순서로 스키마를 재현할 수 있는가?
- JPA 매핑과 실제 스키마가 일치하는가?
- 동시에 요청해도 지켜야 할 불변식이 DB에서 보호되는가?
- 트랜잭션과 잠금이 운영 DB와 같은 MySQL에서 의도대로 동작하는가?
- Index가 실제 SQL을 개선하며 컬럼 순서를 설명할 수 있는가?

## 2. 데이터베이스 소유권

API와 Worker는 하나의 MySQL 서버를 사용할 수 있지만 논리 DB, 계정과 Migration 이력을 분리한다.

| 서비스 | 논리 DB | 계정 | 소유 데이터 |
|---|---|---|---|
| API | `onfilm_api` | `onfilm_api_app` | 사용자, Movie, Storyboard, 인증, Job, Outbox |
| Worker | `onfilm_worker` | `onfilm_worker_app` | Inbox와 Worker 처리 상태 |

다음 규칙을 지킨다.

- 각 저장소는 자기 논리 DB의 Migration만 작성한다.
- DB 사이의 FK, JOIN, View, Repository와 분산 트랜잭션을 만들지 않는다.
- API와 Worker는 Kafka 메시지와 인증된 Callback API로만 데이터를 교환한다.
- 같은 물리 서버를 공유하더라도 상대 DB 접근 권한은 부여하지 않는다.
- Worker DB 변경은 Worker 저장소에서 별도로 검증한다.

상세 결정은 [API·Worker DB 소유권 정책](../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md)을 따른다.

## 3. Schema의 단일 기준

Flyway Versioned Migration을 스키마의 단일 정책원으로 사용한다.

| 구성요소 | 책임 |
|---|---|
| Flyway | 테이블, 컬럼, 타입, Constraint, Index와 운영 Reference Data 변경 |
| Hibernate | `ddl-auto: validate`로 Entity와 Schema 불일치 탐지 |
| JPA Mapping | 애플리케이션이 기대하는 컬럼·연관관계·Index 의도 표현 |
| Testcontainers MySQL | Migration과 영속 동작의 실제 검증 환경 |
| H2 | 빠른 단위·슬라이스 테스트의 보조 환경 |

Hibernate의 `create`, `create-drop`, `update`를 공유 MySQL Schema 관리 수단으로 사용하지 않는다. 적용된 Migration은 수정하거나 이름을 바꾸지 않고, 이후 변경은 증가하는 새 버전으로 작성한다.

API Migration 예시는 다음과 같다.

```text
V1__create_initial_schema.sql
V2__seed_standard_genres.sql
V3__strengthen_refresh_token_constraints.sql
V4__strengthen_aggregate_constraints.sql
V5__add_media_maintenance_indexes.sql
```

Worker는 같은 이름의 독립적인 이력을 소유한다.

```text
V1__create_initial_schema.sql
V2__strengthen_inbox_constraints.sql
```

## 4. Schema 변경 작업 흐름

### 4.1 변경 여부 판단

다음 변경은 모두 Schema 변경으로 취급한다.

- Entity·Table·Column 이름 또는 타입 변경
- 길이, precision, scale, nullability, default, enum 저장 방식 변경
- 연관관계, Join Column, Collection Table, Order Column 변경
- UNIQUE, CHECK, FK, Index 또는 `@Version` 변경
- 운영 Reference Data 추가·수정
- 새 Index에 의존하는 Repository·Native SQL 변경

순수 Java 검증이나 상태 메서드 변경처럼 영속 표현이 같을 때만 Migration을 생략한다.

### 4.2 같은 작업 단위에서 변경

Entity Mapping과 Migration을 같은 작업 단위에서 함께 변경한다. 다음 항목을 양쪽에서 대조한다.

- 테이블·컬럼 이름
- 타입과 길이
- nullability
- enum 저장 방식
- Constraint와 Index 이름 및 컬럼 순서
- FK의 `ON DELETE` 정책

### 4.3 MySQL 검증

Schema 변경은 다음 순서로 검증한다.

1. 빈 MySQL에 V1부터 모든 Migration 적용
2. Hibernate `ddl-auto: validate`로 전체 Mapping 확인
3. 영향을 받는 Repository·트랜잭션 테스트 실행
4. 새 Constraint가 잘못된 값을 실제로 거부하는 테스트 추가
5. UNIQUE 또는 Lock이 정확성 근거라면 동시 요청 테스트 추가
6. 전체 `./gradlew check` 실행
7. Index 변경이면 같은 데이터와 SQL의 `EXPLAIN ANALYZE` 전후 비교

## 5. Constraint 작성 원칙

### 5.1 애플리케이션 검증과 DB 검증을 함께 유지

서비스 사전 조회와 엔티티 검증은 빠르고 구체적인 오류를 제공한다. DB Constraint는 다른 쓰기 경로와 경쟁 조건까지 막는 최종 방어선이다.

| 불변식 | 애플리케이션 책임 | DB 책임 |
|---|---|---|
| 필수값 | DTO·Entity에서 빠르게 거부 | `NOT NULL` |
| 중복 금지 | Repository 사전 조회와 도메인 오류 | `UNIQUE`로 동시 INSERT 경쟁 차단 |
| 참조 무결성 | 존재·소유권 검증 | FK와 명시적인 `ON DELETE` |
| 값 범위 | Entity·DTO 경계값 검증 | 필요한 `CHECK` |
| 동시 상태 변경 | 상태 전이 검증 | `@Version` 기반 optimistic lock |

DB 예외는 제약 이름을 기준으로 알려진 도메인 오류로 변환한다. 식별할 수 없는 위반은 공통 데이터 무결성 오류로 처리한다.

### 5.2 UNIQUE

- 동시 요청에서도 반드시 하나만 존재해야 하는 값에 적용한다.
- 서비스의 `existsBy*`만으로 중복을 보장하지 않는다.
- 복합 UNIQUE는 컬럼 조합이 표현하는 비즈니스 의미를 문서화한다.
- 제약 이름은 `uk_<table>_<meaning>` 형식의 안정적인 이름을 사용한다.

### 5.3 Foreign Key와 삭제 정책

- JPA cascade와 DB `ON DELETE`를 같은 기능으로 취급하지 않는다.
- JPA cascade는 Aggregate를 통한 ORM 작업 전파, FK는 모든 SQL 경로의 참조 무결성을 담당한다.
- 부모와 생명주기를 공유하는 자식은 JPA cascade·orphanRemoval과 DB 삭제 정책을 일치시킨다.
- 이력 보존을 위한 ID-only 약한 관계는 FK를 기계적으로 추가하지 않고 대상 삭제 후 의미를 검토한다.
- FK 이름은 `fk_<child>_<parent>` 형식으로 명시한다.

### 5.4 Nullable과 OrderColumn

null은 단순 누락이 아니라 도메인 상태를 표현할 수 있다. 편집 중인 빈 Scene·Card처럼 null이 유효한 이유를 기록한다.

`@OrderColumn`은 Hibernate가 자식을 먼저 INSERT한 뒤 순서를 UPDATE할 수 있다. 이 저장 절차 때문에 정상 저장이 실패한다면 DB 컬럼을 nullable로 유지하고 다음 방어를 조합한다.

- Aggregate 재정렬 메서드가 정확한 순열 검증
- 음수 순서를 DB CHECK로 차단
- 트랜잭션 완료 후 순서가 채워졌는지 MySQL Repository 테스트

부모별 `(parent_id, sort_order)` UNIQUE도 재정렬 도중 일시 충돌할 수 있으므로 최종 상태만 보고 추가하지 않는다.

### 5.5 문자열과 Collation

문자열의 의미에 따라 비교 정책을 선택한다.

- 사용자 검색용 정규화 문자열은 대소문자 비구분 Collation을 사용할 수 있다.
- Token Hash처럼 각 문자가 식별자의 일부라면 binary Collation으로 정확 비교한다.
- Java의 비교 의미와 DB WHERE·UNIQUE의 비교 의미가 같은지 MySQL에서 검증한다.

## 6. 트랜잭션과 잠금

- DB에서 함께 성공하거나 실패해야 하는 변경만 한 로컬 트랜잭션에 둔다.
- Kafka, S3, FFmpeg와 BCrypt 같은 외부·고비용 작업을 DB 트랜잭션 안에서 기다리지 않는다.
- Outbox 선점 트랜잭션은 상태만 짧게 변경하고 Kafka 발행 전에 commit한다.
- 별도로 보존해야 하는 보안 기록은 의도가 명확한 `REQUIRES_NEW` 경계에서 저장한다.

잠금은 문제에 따라 선택한다.

| 상황 | 기본 선택 | 이유 |
|---|---|---|
| 작업 선점처럼 읽은 즉시 다른 실행을 막아야 함 | 비관적 잠금 | 동일 행을 동시에 처리하지 않도록 직렬화 |
| Refresh Token·Job 최종 상태처럼 충돌이 드묾 | 낙관적 잠금 | 평상시 잠금 대기 없이 version 충돌 탐지 |
| 이메일·사용자명 동시 생성 | DB UNIQUE | 사전 조회 뒤의 INSERT 경쟁을 최종 차단 |

Lock 테스트는 두 스레드와 독립 트랜잭션을 사용하고, 승리한 스레드가 아니라 최종 불변식을 검증한다. H2 결과를 InnoDB 잠금의 증거로 사용하지 않는다.

## 7. Reference Data와 Fixture

데이터의 목적에 따라 관리 위치를 나눈다.

| 데이터 | 관리 위치 | 규칙 |
|---|---|---|
| 모든 환경에 필요한 기준 데이터 | Flyway Versioned Migration | 명시적 ID와 변경 이력 관리 |
| 로컬 화면 확인용 데이터 | `dev` 전용 Initializer | 운영 프로필에서 실행 금지 |
| 자동화 테스트 데이터 | 각 테스트 Fixture | 실행 순서와 로컬 DB 상태에 독립 |
| 성능 측정용 대량 데이터 | 전용 benchmark SQL | 운영·개발 Fixture로 사용 금지 |

전역 `data.sql`에 운영 기준 데이터와 예제 계정을 섞지 않는다. 상세 정책은 [Reference Data와 Fixture 정책](../decisions/reference-data-and-fixture-policy.md)을 따른다.

## 8. Index 선정과 검증

Index는 Repository 이름이나 추측이 아니라 실제 SQL과 실행 계획을 근거로 추가한다.

1. 주요 SQL과 재현 가능한 데이터 분포를 기록한다.
2. `EXPLAIN ANALYZE`로 접근 경로, 실제 읽은 행과 정렬을 측정한다.
3. WHERE의 동등 조건, 범위 조건, JOIN과 ORDER BY를 기준으로 컬럼 순서를 정한다.
4. 기존 Index의 leftmost prefix와 용도가 중복되는지 확인한다.
5. 같은 MySQL 버전·데이터·SQL로 적용 전후를 비교한다.
6. 개선되지 않는 후보는 추가하지 않는다.
7. 조회 개선과 함께 INSERT·UPDATE 비용 및 저장 공간을 기록한다.

예를 들어 `status = ? AND completed_at < ?`는 동등 조건인 `status` 뒤에 범위 조건인 `completed_at`을 둔다. 서로 두 번째 컬럼이 다른 `(status, requested_at)`과 `(status, completed_at)`은 각각 다른 시간 조건을 지원하므로 단순 중복으로 보지 않는다.

## 9. 테스트 계층

| 계층 | 목적 | DB |
|---|---|---|
| 단위 테스트 | Entity 불변식, 상태 전이, Service 분기 | DB 없음 또는 Mock |
| 빠른 JPA Slice | 일반 Mapping과 Repository 회귀 | H2 보조 사용 가능 |
| MySQL 통합 테스트 | Flyway, validate, Constraint, FK, Transaction, Lock, 동시성 | Testcontainers MySQL 8.4.11 |
| 성능 실험 | 실제 SQL의 접근 경로와 상대 변화 | 전용 MySQL benchmark 컨테이너 |

CI는 단위 테스트와 MySQL 통합 테스트를 별도 작업으로 실행한다. Docker에 접근할 수 없다는 이유로 MySQL 테스트를 성공으로 건너뛰지 않는다.

## 10. 완료 체크리스트

- [ ] 변경 대상 DB와 소유 저장소가 명확하다.
- [ ] 새 Versioned Migration을 작성했고 기존 Migration을 수정하지 않았다.
- [ ] Entity Mapping과 Migration의 이름·타입·제약·Index가 일치한다.
- [ ] 모든 FK의 삭제 의미를 검토했다.
- [ ] 운영 Reference Data와 개발·테스트 Fixture를 분리했다.
- [ ] 빈 MySQL에서 전체 Migration이 적용된다.
- [ ] Hibernate `validate`를 통과한다.
- [ ] 새 Constraint의 허용·거부 경계 테스트가 있다.
- [ ] 필요한 동시성·잠금 테스트가 실제 MySQL에서 통과한다.
- [ ] Index는 적용 전후 EXPLAIN 근거와 컬럼 순서 설명이 있다.
- [ ] `./gradlew check`가 통과한다.
- [ ] 정책·감사·성능 문서와 README 링크를 갱신했다.

## 11. 피해야 할 방식

- 운영·공유 DB에서 `ddl-auto: update` 사용
- 이미 적용된 Flyway Migration 수정
- Entity만 바꾸고 Migration을 누락
- H2 성공을 MySQL 호환성의 최종 증거로 사용
- 사전 중복 조회만 믿고 DB UNIQUE 생략
- JPA cascade만 보고 FK `ON DELETE`를 결정
- 모든 ID-only 관계에 FK를 일괄 적용
- 모든 Entity에 `@Version`을 일괄 적용
- 실행 계획 없이 Composite Index 추가
- 성능 데이터와 개발 Fixture를 운영 Migration에 포함
- API와 Worker DB를 직접 JOIN하거나 한 트랜잭션으로 묶기

## 관련 문서

- [저장소 DB Schema 변경 규칙](../../AGENTS.md)
- [API·Worker DB 소유권과 Flyway 정책](../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md)
- [MySQL Constraint 감사](../review/database/mysql-constraint-audit.md)
- [MySQL Testcontainers 통합 테스트](../testing/mysql-testcontainers.md)
- [MySQL 트랜잭션과 잠금 테스트](../testing/mysql-transaction-and-locking.md)
- [Index 적용 전 기준선](../performance/mysql-index-baseline.md)
- [Index 적용 전후 비교](../performance/mysql-index-before-after-comparison.md)
