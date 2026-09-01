# 운영 Reference Data와 개발·테스트 Fixture 정책

- 상태: Accepted
- 결정일: 2026-09-01
- 적용 대상: OnFilm API 데이터 초기화

## 배경

기존 `data.sql`에는 모든 환경에서 필요한 표준 장르와 로컬 화면 확인용 사용자·프로필·스토리보드가 함께 들어 있었다. Spring SQL 초기화와 Hibernate 스키마 생성 순서에도 의존했고, 별도의 개발 계정 초기화 코드 두 개가 서로 다른 계정을 추가했다.

이 구조에서는 다음을 구분하기 어려웠다.

- 운영 DB에도 반드시 존재해야 하는 데이터
- 개발자가 로컬에서 화면과 쿼리를 확인하기 위한 예제 데이터
- 자동화 테스트가 독립적으로 준비해야 하는 검증 데이터

## 결정

데이터를 목적과 생명주기에 따라 세 종류로 분리한다.

| 분류 | 현재 데이터 | 관리 방식 | 적용 환경 |
|---|---|---|---|
| 운영 Reference Data | 표준 장르 19개 | Flyway Versioned Migration | MySQL 개발·통합 테스트·운영 |
| 개발 Fixture | 테스트 사용자·프로필·SNS·태그·갤러리·프로젝트 10개·씬 30개 | `dev` 프로필 전용 `DevDataInitializer` | 로컬 개발 |
| 테스트 Fixture | 테스트별 필요한 엔티티 | 각 테스트의 setup 또는 fixture factory | 자동화 테스트 |

전역 `data.sql`은 사용하지 않는다. 스키마 생성은 Flyway, 운영 Reference Data 이력은 Flyway Versioned Migration, 개발 Fixture 생성은 프로필이 제한된 애플리케이션 코드가 각각 담당한다.

## 표준 장르 정책

초기 표준 장르 19개는 `V2__seed_standard_genres.sql`에서 명시적인 ID와 함께 저장한다. 빈 DB에서 같은 장르가 같은 ID를 갖게 해 API 요청과 환경 간 재현성을 높인다.

- 기존 V2를 수정하지 않는다.
- 필수 장르 추가·이름 변경·비활성화가 모든 환경에 적용되어야 하면 새 Versioned Migration을 작성한다.
- `INSERT IGNORE`나 upsert로 예상하지 못한 기존 데이터를 덮어쓰거나 조용히 무시하지 않는다.
- Repeatable Migration으로 현재 상태를 반복 덮어쓰지 않는다. 운영 중 관리자가 비활성화한 상태를 애플리케이션 재시작이 되돌릴 수 있기 때문이다.

## 개발 Fixture 정책

`DevDataInitializer`는 `dev` 프로필에서만 실행하며 개발 이메일 존재 여부를 기준으로 중복 생성을 막는다. SQL로 FK와 정렬 순서를 직접 맞추지 않고 엔티티 팩토리와 연관관계 메서드를 사용해 현재 도메인 규칙을 따른다.

개발 Fixture에는 공개 운영에 사용하면 안 되는 고정 로그인 정보가 포함된다. `dev` 프로필을 운영에서 활성화하지 않으며, 운영 데이터 복구나 기준 데이터 생성 수단으로 사용하지 않는다.

## 테스트 Fixture 정책

빠른 단위·슬라이스 테스트는 H2를 보조 수단으로 사용할 수 있지만 `data.sql`을 자동 로드하지 않는다. 각 테스트는 필요한 데이터만 직접 생성해 실행 순서와 외부 초기 데이터에 의존하지 않는다.

Flyway Reference Data 자체는 MySQL Testcontainers 통합 테스트에서 다음을 검증한다.

- V2가 빈 MySQL에 성공적으로 적용된다.
- 표준 장르가 19개 생성된다.
- 명시한 ID와 정규화 이름이 유지된다.
- 장르 자동완성 Repository 조회가 실제 MySQL에서 동작한다.

## 기술 선택과 트레이드오프

### 선택한 방식의 장점

- 운영 필수 데이터가 스키마 버전과 함께 동일한 순서로 배포된다.
- 개발 예제 데이터가 운영 DB에 섞이지 않는다.
- 테스트가 로컬 데이터 상태와 실행 순서에 독립적이다.
- 개발 Fixture도 도메인 메서드를 사용하므로 엔티티 규칙 변경을 빠르게 발견할 수 있다.

### 감수한 비용

- Reference Data 변경에도 새 Migration과 검토가 필요하다.
- 로컬 개발은 MySQL과 Flyway가 먼저 실행되어야 한다.
- 개발 Fixture가 커지면 시작 시간이 늘어나므로 대용량 성능 데이터는 별도 스크립트로 관리해야 한다.
- H2 단위 테스트는 Reference Data 존재를 자동으로 가정할 수 없다.

## 관련 파일

- `src/main/resources/db/migration/V2__seed_standard_genres.sql`
- `src/main/java/com/onfilm/domain/common/config/DevDataInitializer.java`
- `src/integrationTest/java/com/onfilm/domain/genre/GenreReferenceDataIntegrationTest.java`
- `src/main/resources/application-dev.yml`

## 관련 문서

- [API와 Worker의 DB 소유권 및 Flyway 초기화 정책](api-worker-database-ownership-and-flyway-baseline-policy.md)
- [API MySQL Testcontainers 통합 테스트 환경](../testing/mysql-testcontainers.md)
