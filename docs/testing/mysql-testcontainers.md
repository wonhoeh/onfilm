# API MySQL Testcontainers 통합 테스트 환경

- 적용일: 2026-09-01
- 적용 범위: OnFilm API `integrationTest` 소스셋

## 목적

H2의 MySQL 호환 모드는 SQL 문법, 타입, Constraint, Lock과 트랜잭션 동작을 MySQL과 완전히 같게 재현하지 않는다. DB 동작이 테스트 결과에 영향을 주는 통합 테스트는 실제 MySQL 컨테이너에서 실행한다.

Testcontainers는 테스트 실행 시 격리된 MySQL을 시작하고 JDBC 접속 정보를 Spring에 동적으로 전달한다. 개발자가 고정 포트의 로컬 DB를 미리 만들 필요가 없으며 CI에서도 같은 이미지와 설정을 사용할 수 있다.

## 구성

| 항목 | 값 |
|---|---|
| 이미지 | `mysql:8.4.11` |
| 논리 DB | `onfilm_api` |
| 계정 | `onfilm_api_app` |
| 문자 집합 | `utf8mb4` |
| Collation | `utf8mb4_0900_ai_ci` |
| Testcontainers | `1.21.4` |
| 호스트 포트 | Testcontainers가 임의 할당 |
| 공통 지원 클래스 | `MySqlContainerSupport` |

통합 테스트 클래스는 `MySqlContainerSupport`를 상속하고 `@AutoConfigureTestDatabase(replace = NONE)`을 선언한다. 이를 통해 `@DataJpaTest`가 실제 MySQL Datasource를 H2로 교체하지 못하게 한다.

컨테이너는 integrationTest JVM에서 한 번 시작해 테스트 클래스들이 공유한다. 테스트 JVM이 종료되면 Testcontainers의 Resource Reaper가 컨테이너를 정리한다. 실험적인 reusable container 기능은 사용하지 않아 이전 실행의 데이터가 다음 실행에 남지 않는다.

## 로컬 Compose와의 관계

`infra/mysql`의 Compose는 API와 Worker를 직접 실행할 때 사용하는 개발 DB다. Testcontainers는 테스트 전용 MySQL을 별도로 만들기 때문에 다음 항목을 공유하지 않는다.

- 호스트 `3306` 포트
- 로컬 Compose Volume
- 로컬 `.env` 비밀번호
- 개발자가 직접 저장한 데이터

따라서 로컬 MySQL이 실행 중이어도 Testcontainers 통합 테스트와 포트 또는 데이터가 충돌하지 않는다. Docker Engine은 실행 중이어야 한다.

## 실행

API 저장소 루트에서 다음 명령을 실행한다.

```bash
./gradlew integrationTest
```

단위 테스트와 통합 테스트를 모두 실행하려면 다음 명령을 사용한다.

```bash
./gradlew check
```

Docker 상태는 다음 명령으로 확인한다.

```bash
docker info
```

Docker가 없거나 데몬에 접근할 수 없으면 통합 테스트를 성공으로 건너뛰지 않고 실패시킨다. CI가 MySQL 검증을 수행했다는 사실을 보장하기 위한 정책이다.

Pull Request와 `main`·`test` 브랜치 Push에서는 별도의 `API CI / MySQL integration tests` 작업이 같은 명령을 실행한다. 실행 조건, 실패 보고서와 병합 정책은 [API CI 검증 정책](api-ci.md)을 따른다.

Spring Boot 3.3.4의 기본 의존성 관리가 제공하는 Testcontainers 1.19.8은 최소 Docker API 1.40을 요구하는 최근 Docker Engine과 통신할 때 HTTP 400이 발생할 수 있다. 2.x는 모듈 artifact와 Java package가 변경되는 메이저 버전이므로, 현재는 기존 API와 호환되면서 최근 Docker Engine 변경을 지원하는 1.x 최신 유지보수 버전인 1.21.4를 명시적으로 고정한다.

## 현재 통합 테스트 범위

- 빈 MySQL에 V1부터 모든 Flyway Versioned Migration 적용
- `flyway_schema_history`의 성공한 Migration 버전 확인
- Hibernate `ddl-auto: validate`를 통한 엔티티와 스키마 일치 검증
- V2 표준 장르의 개수·고정 ID·정규화 값과 자동완성 조회 확인
- MySQL 버전, 논리 DB, 계정, 문자 집합과 Collation 확인
- Movie 참여 이력의 복수 역할 fetch와 `sort_order` 기반 조회 순서 확인
- Movie의 Trailer·Genre orphanRemoval과 Movie 삭제 cascade 확인
- Storyboard Scene·Card 재정렬의 `sort_order` 저장과 중첩 자식 삭제 확인
- User 정규화 조회와 이메일·사용자명 UNIQUE, 필수값, Person FK 위반 거부 확인
- User 삭제 시 생명주기를 공유하는 Person 삭제 확인
- Job·Outbox의 commit·rollback 원자성과 `REQUIRES_NEW` 보안 기록 보존 확인
- UploadRequest 비관적 잠금의 대기·커밋 후 최신 상태 조회 확인
- Outbox 동시 선점 시 동일 행의 중복 claim 방지 확인
- Outbox 선점 후 프로세스 종료를 가정한 lease 만료 복구
- 반복 발행 실패 후 Outbox `DEAD` 전환
- Job과 Outbox 저장 중 DB 오류가 발생했을 때 전체 rollback
- 중복 완료 Callback의 no-op과 결과 중복 방지
- `DONE`과 `FAILED` 사이의 역순 Callback 거부

## Flyway와 Hibernate 역할

MySQL 통합 테스트와 운영 프로필은 Flyway가 스키마를 생성하고 Hibernate가 매핑 일치 여부만 검증한다.

1. Testcontainers가 빈 `onfilm_api` DB를 시작한다.
2. Flyway가 V1 스키마와 이후 Versioned Migration을 순서대로 적용한다.
3. Hibernate가 `ddl-auto: validate`로 전체 엔티티 매핑을 검증한다.
4. Migration 또는 Validation이 실패하면 Spring Context가 시작되지 않아 테스트가 실패한다.

`baselineOnMigrate`는 사용하지 않는다. 보존할 운영 데이터가 없는 현재 정책에 따라 항상 빈 DB에서 V1부터 전체 Migration을 적용한다.

빠른 단위·슬라이스 테스트만 H2와 Hibernate `create-drop`을 보조적으로 사용하며 자동 `data.sql`은 로드하지 않는다. 개발 프로필은 로컬 MySQL에서 Flyway와 Hibernate `validate`를 사용한다. H2 결과를 MySQL 스키마 호환성의 증거로 사용하지 않는다.

트랜잭션과 행 잠금의 동시 실행 방식은 [MySQL 트랜잭션과 잠금 통합 테스트](mysql-transaction-and-locking.md)에 정리한다.
