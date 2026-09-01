# MySQL 트랜잭션과 잠금 통합 테스트

- 적용일: 2026-09-01
- DB: Testcontainers `mysql:8.4.11`
- 테스트: `MySqlTransactionBoundaryIntegrationTest`, `MySqlPessimisticLockIntegrationTest`

## 목적

트랜잭션 annotation의 존재가 아니라 실제 MySQL 연결에서 commit·rollback·트랜잭션 전파와 행 잠금이 의도한 결과를 만드는지 검증한다. H2의 트랜잭션과 잠금 구현은 MySQL InnoDB와 같지 않으므로 동시 요청의 정합성 근거로 사용하지 않는다.

## 검증 시나리오

| 시나리오 | 기대 결과 |
|---|---|
| Job과 Outbox 정상 저장 | 두 데이터가 함께 commit되어 이후 트랜잭션에서 조회됨 |
| Job과 Outbox 저장 중 예외 | 두 데이터가 모두 rollback되어 남지 않음 |
| 외부 트랜잭션에서 보안 기록 후 401 예외 | 외부 변경은 rollback되고 `REQUIRES_NEW` 보안 기록은 유지됨 |
| 같은 UploadRequest를 두 트랜잭션이 잠금 조회 | 두 번째 트랜잭션은 첫 번째 commit까지 대기하고 commit된 최신 상태를 읽음 |
| 같은 Outbox를 두 Publisher가 선점 | 두 번째 트랜잭션은 잠금 해제 후 빈 결과를 받고 attempts는 한 번만 증가함 |

## 동시성 테스트 방식

각 동시성 테스트는 두 개의 스레드와 두 개의 독립 트랜잭션을 사용한다.

1. 첫 번째 트랜잭션이 `PESSIMISTIC_WRITE`로 대상 행을 잠근다.
2. `CountDownLatch`로 잠금 획득을 확인한 뒤 두 번째 트랜잭션을 시작한다.
3. 두 번째 작업이 500ms 안에 완료되지 않는 것으로 잠금 대기를 확인한다.
4. 첫 번째 트랜잭션의 commit을 허용한다.
5. 두 번째 트랜잭션이 최신 상태를 읽거나 이미 선점된 Outbox를 제외하는지 확인한다.

500ms는 DB 성능 기준이 아니라 “첫 트랜잭션이 의도적으로 잠금을 유지하는 동안 두 번째 작업이 먼저 완료되지 않는다”는 순서 검증용 시간이다. 최종 완료에는 10초 제한을 두어 잠금이 해제되지 않는 회귀가 CI를 무기한 대기시키지 않게 한다.

## 보장 범위와 한계

- 테스트는 단일 MySQL 인스턴스의 InnoDB 행 잠금과 현재 Repository query를 검증한다.
- 처리량이나 평균 lock wait time을 측정하는 성능 테스트가 아니다.
- deadlock 발생 시 재시도 정책은 아직 검증하지 않는다.
- `@Version` 기반 낙관적 락과 동시 INSERT의 UNIQUE 충돌은 후속 동시성 단계에서 검증한다.
- Outbox lock은 Kafka 발행 중 유지하지 않는다. DB 선점 transaction이 commit된 다음 트랜잭션 밖에서 Kafka를 호출한다.

## 실행

Docker Engine이 실행 중인 API 저장소 루트에서 다음 명령을 사용한다.

```bash
./gradlew integrationTest \
  --tests '*MySqlTransactionBoundaryIntegrationTest' \
  --tests '*MySqlPessimisticLockIntegrationTest'
```

전체 검증은 다음 명령으로 실행한다.

```bash
./gradlew check
```

## 관련 문서

- [Transaction Boundary 설계 가이드](../review/transaction/transaction-boundary-guide.md)
- [API MySQL Testcontainers 통합 테스트 환경](mysql-testcontainers.md)
- [미디어 Job과 Transactional Outbox 정책](../decisions/media-encode-job-outbox-policy.md)
