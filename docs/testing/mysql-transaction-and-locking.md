# MySQL 트랜잭션과 잠금 통합 테스트

- 적용일: 2026-09-01
- DB: Testcontainers `mysql:8.4.11`
- 테스트: `MySqlTransactionBoundaryIntegrationTest`, `MySqlPessimisticLockIntegrationTest`, `MySqlUniqueAndOptimisticLockIntegrationTest`

## 목적

트랜잭션 annotation의 존재가 아니라 실제 MySQL 연결에서 commit·rollback·트랜잭션 전파, UNIQUE 경쟁과 비관적·낙관적 락이 의도한 결과를 만드는지 검증한다. H2의 트랜잭션과 잠금 구현은 MySQL InnoDB와 같지 않으므로 동시 요청의 정합성 근거로 사용하지 않는다.

## 검증 시나리오

| 시나리오 | 기대 결과 |
|---|---|
| Job과 Outbox 정상 저장 | 두 데이터가 함께 commit되어 이후 트랜잭션에서 조회됨 |
| Job과 Outbox 저장 중 예외 | 두 데이터가 모두 rollback되어 남지 않음 |
| 외부 트랜잭션에서 보안 기록 후 401 예외 | 외부 변경은 rollback되고 `REQUIRES_NEW` 보안 기록은 유지됨 |
| 같은 UploadRequest를 두 트랜잭션이 잠금 조회 | 두 번째 트랜잭션은 첫 번째 commit까지 대기하고 commit된 최신 상태를 읽음 |
| 같은 Outbox를 두 Publisher가 선점 | 두 번째 트랜잭션은 잠금 해제 후 빈 결과를 받고 attempts는 한 번만 증가함 |
| 같은 이메일로 회원가입 INSERT | 두 요청이 사전 조회를 모두 통과해도 DB UNIQUE가 하나만 commit하고 나머지는 `uk_users_email` 위반 |
| 같은 사용자명으로 회원가입 INSERT | 하나만 commit되고 나머지는 `uk_users_username_normalized` 위반 |
| 같은 Refresh Token을 동시에 소비 | 같은 version을 읽어도 하나만 폐기하며 나머지는 낙관적 락 충돌 |
| 같은 Media Job을 동시에 DONE·FAILED 전환 | 하나의 최종 상태만 commit되고 반대 전이는 낙관적 락 충돌 |

## 동시성 테스트 방식

각 동시성 테스트는 두 개의 스레드와 두 개의 독립 트랜잭션을 사용한다. 아래 순서 제어는 비관적 락 테스트에 해당한다.

1. 첫 번째 트랜잭션이 `PESSIMISTIC_WRITE`로 대상 행을 잠근다.
2. `CountDownLatch`로 잠금 획득을 확인한 뒤 두 번째 트랜잭션을 시작한다.
3. 두 번째 작업이 500ms 안에 완료되지 않는 것으로 잠금 대기를 확인한다.
4. 첫 번째 트랜잭션의 commit을 허용한다.
5. 두 번째 트랜잭션이 최신 상태를 읽거나 이미 선점된 Outbox를 제외하는지 확인한다.

500ms는 DB 성능 기준이 아니라 “첫 트랜잭션이 의도적으로 잠금을 유지하는 동안 두 번째 작업이 먼저 완료되지 않는다”는 순서 검증용 시간이다. 최종 완료에는 10초 제한을 두어 잠금이 해제되지 않는 회귀가 CI를 무기한 대기시키지 않게 한다.

## 동시 INSERT와 UNIQUE

사전 중복 조회는 빠르고 구체적인 오류 응답을 제공하지만 동시 요청을 직렬화하지 않는다. 테스트는 두 트랜잭션이 각각 `existsByEmail`과 `existsByUsernameNormalized`에서 `false`를 확인한 뒤 Barrier에서 대기하도록 구성한다. 두 요청이 동시에 INSERT하면 MySQL UNIQUE가 정확히 하나만 commit한다.

MySQL 8.4가 Hibernate에 전달하는 실제 제약 이름은 `users.uk_users_email`처럼 테이블명이 포함될 수 있다. `AuthTransactionService`는 전체 일치가 아닌 대소문자 무시 부분 일치로 이를 식별하므로 다음 API 정책을 유지한다.

- `uk_users_email` 경쟁 패배: `409 DUPLICATE_EMAIL`
- `uk_users_username_normalized` 경쟁 패배: `409 DUPLICATE_USERNAME`
- 식별할 수 없는 제약 위반: `409 DATA_INTEGRITY_VIOLATION`

## 낙관적 락

두 독립 트랜잭션이 같은 version을 읽도록 Barrier로 맞춘 뒤 서로 다른 변경을 flush한다. Hibernate의 UPDATE는 식별자와 이전 version을 함께 조건으로 사용하므로 하나가 version을 증가시키면 나머지 UPDATE는 0행이 되어 `OptimisticLockingFailureException`이 발생한다.

| 대상 | 충돌 정책 | API 응답 |
|---|---|---|
| Refresh Token 동시 회전 | 첫 요청만 기존 토큰 소비, 두 번째 요청은 새 토큰을 발급하지 않음 | `401 INVALID_REFRESH_TOKEN` |
| Media Job 동시 상태 변경 | 첫 최종 상태만 유지, 두 번째 트랜잭션 전체 rollback | `409 CONCURRENT_MEDIA_JOB_UPDATE` |

`@Version`은 상태 경쟁이 이미 존재하는 Refresh Token, MediaUploadRequest, MediaEncodeJob, MediaEncodeOutbox에만 유지한다. Movie·Person 같은 일반 Aggregate에 일괄 추가하면 컬렉션의 서로 무관한 변경까지 충돌하고 클라이언트 version 계약도 필요해지므로 이번 단계에서는 추가하지 않는다.

## 보장 범위와 한계

- 테스트는 단일 MySQL 인스턴스의 InnoDB 행 잠금과 현재 Repository query를 검증한다.
- 처리량이나 평균 lock wait time을 측정하는 성능 테스트가 아니다.
- 동시성 시작 순서는 Barrier로 제어하지만 어떤 스레드가 승리할지는 가정하지 않는다. 최종 결과만 하나인지 검증한다.
- DB deadlock과 lock wait timeout의 재시도 정책은 아직 검증하지 않는다.
- Outbox lock은 Kafka 발행 중 유지하지 않는다. DB 선점 transaction이 commit된 다음 트랜잭션 밖에서 Kafka를 호출한다.

## 실행

Docker Engine이 실행 중인 API 저장소 루트에서 다음 명령을 사용한다.

```bash
./gradlew integrationTest \
  --tests '*MySqlTransactionBoundaryIntegrationTest' \
  --tests '*MySqlPessimisticLockIntegrationTest' \
  --tests '*MySqlUniqueAndOptimisticLockIntegrationTest'
```

전체 검증은 다음 명령으로 실행한다.

```bash
./gradlew check
```

## 관련 문서

- [Transaction Boundary 설계 가이드](../review/transaction/transaction-boundary-guide.md)
- [API MySQL Testcontainers 통합 테스트 환경](mysql-testcontainers.md)
- [미디어 Job과 Transactional Outbox 정책](../decisions/media-encode-job-outbox-policy.md)
