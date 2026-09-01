# API CI 검증 정책

- 적용일: 2026-09-01
- Workflow: `.github/workflows/api-ci.yml`
- 적용 대상: OnFilm API 저장소

## 목적

빠른 단위 테스트와 실제 MySQL 동작 검증을 독립된 CI 작업으로 실행한다. H2 테스트만 통과하고 MySQL의 Migration, Constraint, Lock 또는 트랜잭션 차이로 실패하는 변경이 병합되는 것을 방지한다.

## 실행 조건

`API CI`는 다음 조건에서 실행한다.

- `main` 또는 `test`를 대상으로 한 Pull Request
- `main` 또는 `test` 브랜치 Push
- GitHub Actions 화면의 수동 실행

같은 브랜치에 새 커밋이 Push되면 이전 실행을 취소하고 최신 커밋을 검증한다. Workflow 권한은 저장소 내용 읽기로 제한하며 애플리케이션 Secret은 사용하지 않는다.

## 검증 작업

두 작업은 서로 의존하지 않고 병렬로 실행한다.

| CI 작업명 | 명령 | DB | 검증 범위 | 제한 시간 |
|---|---|---|---|---:|
| `Unit tests` | `./gradlew test --no-daemon` | H2 또는 DB 없는 단위 테스트 | 도메인·서비스·Web·보안의 빠른 회귀 검증 | 15분 |
| `MySQL integration tests` | `./gradlew integrationTest --no-daemon` | Testcontainers MySQL 8.4.11 | Flyway, Hibernate validate, Repository, Transaction, Lock과 장애 시나리오 | 20분 |

MySQL 통합 테스트는 별도 GitHub Actions MySQL Service를 사용하지 않는다. Testcontainers가 GitHub-hosted Ubuntu Runner의 Docker Engine에 격리된 MySQL을 생성하고 테스트 종료 시 정리한다. `docker info`가 실패하면 통합 테스트를 건너뛰지 않고 CI를 실패시킨다.

## 실패 진단

테스트가 실패하면 해당 작업이 다음 디렉터리를 7일 동안 Artifact로 보관한다.

- 단위 테스트: `build/reports/tests/test`, `build/test-results/test`
- MySQL 통합 테스트: `build/reports/tests/integrationTest`, `build/test-results/integrationTest`

먼저 실패한 테스트 이름과 stack trace를 확인한 후, Flyway 또는 Spring Context 시작 실패라면 통합 테스트 로그에서 가장 처음 발생한 `Caused by`를 확인한다.

## 로컬 재현

```bash
./gradlew test --no-daemon
docker info
./gradlew integrationTest --no-daemon
```

두 테스트 묶음과 기타 Gradle 검증을 한 번에 실행하려면 다음 명령을 사용한다.

```bash
./gradlew check --no-daemon
```

## 병합과 배포 정책

기존 `Deploy To EC2` Workflow는 배포 산출물 생성과 전송을 담당하며 현재 `-x test` 옵션을 사용한다. 따라서 `API CI` 추가만으로 배포 Workflow 실행 순서가 자동으로 보장되지는 않는다.

GitHub Branch Protection에서 다음 상태 검사를 필수로 지정해 두 검증이 성공한 Pull Request만 `main` 또는 `test`에 병합되도록 운영한다.

- `API CI / Unit tests`
- `API CI / MySQL integration tests`

향후 배포가 Pull Request 병합 외의 경로에서도 실행될 수 있다면, 배포 Workflow를 `workflow_run`으로 연결하거나 배포 작업에 명시적인 CI 의존성을 추가한다.

## 관련 문서

- [API MySQL Testcontainers 통합 테스트 환경](mysql-testcontainers.md)
- [API와 Worker의 DB 소유권 및 Flyway 초기화 정책](../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md)
- [운영 Reference Data와 개발·테스트 Fixture 정책](../decisions/reference-data-and-fixture-policy.md)
