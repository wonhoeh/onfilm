# 미디어 Outbox DEAD·Kafka DLT 재처리 절차

- 작성일: 2026-08-31
- 적용 범위: Onfilm API `media_encode_outbox`, Encoding Worker Inbox, `media.encode.requested.dlt`
- 관련 정책: [미디어 처리 장애 대응 정책](../decisions/media-failure-handling-policy.md)
- 담당: Backend 운영자

## 1. 목적

자동 재시도를 소진한 Outbox `DEAD`와 Kafka DLT를 무조건 다시 실행하지 않고, 원인과 현재 상태를 확인한 뒤 안전한 대상만 한 건씩 복구한다.

두 실패 지점은 의미가 다르다.

- Outbox `DEAD`: API DB에는 Job이 있지만 Kafka 발행을 완료하지 못했다.
- Kafka DLT: Worker까지 메시지가 도착했지만 처리 재시도를 소진했다.

## 2. 공통 안전 규칙

1. 대량 `UPDATE`나 DLT 전체 재발행을 금지한다. `outboxId` 또는 DLT `topic/partition/offset` 한 건을 명시한다.
2. 원인을 수정하고 Kafka·DB·스토리지 상태가 정상화된 뒤 실행한다.
3. API Job이 `DONE` 또는 `FAILED`이면 같은 `jobId`를 다시 실행하지 않는다.
4. Worker Inbox가 `DONE` 또는 `FAILED`이면 상태를 되돌리지 않는다.
5. 원본 파일 존재, payload의 `jobId` 일치와 schema version 지원 여부를 확인한다.
6. 작업자, 실행 시각, 변경 티켓, 사유, 변경 전후 상태와 원본 위치를 감사 기록에 남긴다.
7. 비밀키, 인증 헤더, Presigned URL과 토큰은 명령 이력이나 기록에 남기지 않는다.

필수 감사 항목:

```text
operator, executedAt, ticketId, reason,
outboxId, jobId,
dltTopic, dltPartition, dltOffset,
originalTopic, originalPartition, originalOffset,
beforeStatus, afterStatus, verificationResult
```

## 3. Outbox DEAD 재처리

### 3.1 허용 조건

다음을 모두 만족할 때만 같은 Outbox를 `PENDING`으로 전환한다.

- Outbox 상태가 `DEAD`
- 연결된 API Job 상태가 `REQUESTED`
- 마지막 오류의 원인이 제거됨
- payload의 `schemaVersion`이 현재 지원 버전 `1`
- payload의 `jobId`가 Outbox `job_id`와 같음
- source bucket/key의 원본 객체가 존재함

Job이 `PROCESSING`, `DONE`, `FAILED`이면 재발행하지 않는다. Kafka 발행 성공 후 Outbox 상태 저장만 실패했을 가능성이 있으므로 Worker Inbox와 Kafka 로그를 먼저 조사한다.

### 3.2 변경 전 조회

운영 DB의 읽기 전용 세션에서 대상 한 건을 확인한다.

```sql
SELECT
    o.id AS outbox_id,
    o.job_id,
    o.status AS outbox_status,
    o.attempts,
    o.created_at,
    o.next_attempt_at,
    o.lease_until,
    o.last_error,
    o.schema_version,
    j.status AS job_status,
    j.source_bucket,
    j.source_key
FROM media_encode_outbox o
JOIN media_encode_jobs j ON j.id = o.job_id
WHERE o.id = '<OUTBOX_ID>';
```

조회 결과와 원본 객체 존재 확인 결과를 변경 티켓에 첨부한다.

### 3.3 단건 전환

아래 변경은 `DEAD + REQUESTED` 조건을 다시 확인하는 방어적 UPDATE다. `attempts`를 0으로 초기화하여 새 수동 재처리 회차에 최대 8회 자동 재시도를 다시 허용한다. 기존 `last_error`는 첫 재발행 결과가 기록될 때까지 진단 근거로 유지한다.

```sql
START TRANSACTION;

SELECT o.id, o.job_id, o.status, o.attempts, o.last_error, j.status AS job_status
FROM media_encode_outbox o
JOIN media_encode_jobs j ON j.id = o.job_id
WHERE o.id = '<OUTBOX_ID>'
FOR UPDATE;

UPDATE media_encode_outbox o
JOIN media_encode_jobs j ON j.id = o.job_id
SET o.status = 'PENDING',
    o.attempts = 0,
    o.next_attempt_at = UTC_TIMESTAMP(6),
    o.lease_until = NULL
WHERE o.id = '<OUTBOX_ID>'
  AND o.status = 'DEAD'
  AND j.status = 'REQUESTED';

SELECT ROW_COUNT() AS changed_rows;
COMMIT;
```

`changed_rows`가 정확히 `1`이 아니면 성공으로 기록하지 않는다. `0`이면 상태가 바뀌었거나 조건을 충족하지 않은 것이므로 원인을 다시 확인한다.

### 3.4 확인과 중단 기준

- Publisher 로그에서 같은 `outboxId`, `jobId`의 `MEDIA_ENCODE_OUTBOX_PUBLISHED`를 확인한다.
- DB에서 Outbox가 `PUBLISHED`인지 확인한다.
- 다시 `DEAD`가 되면 추가 초기화하지 않고 새로운 오류를 조사한다.
- Job이 `PROCESSING` 이상으로 진행됐는데 Outbox가 다시 실패하면 Worker Inbox와 중복 전달 여부를 확인한다.

## 4. Kafka DLT 재처리

Spring Kafka는 DLT 레코드에 원본 topic·partition·offset과 실패 정보를 header로 추가한다. Worker는 다음 구조화 로그 필드를 남긴다.

```text
eventType=MEDIA_ENCODE_DLT_RECEIVED
jobId, requestId, kafkaKey,
dltTopic, dltPartition, dltOffset,
originalTopic, originalPartition, originalOffset,
failureType, failureMessage
```

### 4.1 상태별 결정

| API Job | Worker Inbox | 처리 |
|---|---|---|
| `REQUESTED` 또는 `PROCESSING` | 없음 또는 `RETRY_WAIT` | 원인 수정 후 같은 `jobId` DLT 단건 재발행 가능 |
| `REQUESTED` 또는 `PROCESSING` | `FAILURE_PENDING` | Worker를 중지하고 Inbox를 `RETRY_WAIT`으로 전환한 뒤 단건 재발행 가능 |
| `REQUESTED` 또는 `PROCESSING` | `PROCESSING` | lease 만료 복구를 우선 사용하고 DLT 재발행 금지 |
| `REQUESTED` 또는 `PROCESSING` | `OUTPUT_UPLOADED` | Callback-only 복구를 사용하고 인코딩 재실행 금지 |
| `DONE` 또는 `FAILED` | 모든 상태 | 같은 `jobId` 재발행 금지 |
| 모든 상태 | `DONE` 또는 `FAILED` | Inbox 최종 상태를 되돌리지 않음 |
| Job 또는 `jobId` 없음 | 없음 | 잘못된 메시지로 분류하고 직접 재발행 금지 |

API Job이 `FAILED`인 작업을 다시 실행해야 한다면 사용자가 새 업로드 요청과 새 `requestId`로 새 Job을 생성한다. 최종 상태를 되돌리거나 기존 DLT payload의 `jobId`만 바꾸지 않는다.

### 4.2 DLT 보존 기간

DLT는 최소 14일 보존한다. 운영 Kafka의 변경 절차에 따라 다음 설정을 적용하고 결과를 확인한다.

```bash
kafka-configs.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --alter \
  --entity-type topics \
  --entity-name media.encode.requested.dlt \
  --add-config retention.ms=1209600000

kafka-configs.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --describe \
  --entity-type topics \
  --entity-name media.encode.requested.dlt
```

### 4.3 대상 레코드 확인

구조화 로그의 DLT 위치를 사용하여 정확히 한 건만 읽는다. `print.headers=true`는 조사에만 사용하며 header 전체를 원래 토픽으로 복사하지 않는다.

```bash
kafka-console-consumer.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --topic media.encode.requested.dlt \
  --partition "$DLT_PARTITION" \
  --offset "$DLT_OFFSET" \
  --max-messages 1 \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true
```

payload의 `jobId`, `requestId`, source/target key와 로그의 식별자가 같은지 확인한다.

### 4.4 FAILURE_PENDING 복구

DLT handler가 실패 Callback을 이미 보냈을 수 있으므로 먼저 Worker를 중지하고 API Job 상태를 다시 조회한다. API Job이 여전히 `REQUESTED` 또는 `PROCESSING`일 때만 다음 조건부 변경을 수행한다.

```sql
START TRANSACTION;

SELECT job_id, status, attempts, failure_code, failure_reason, updated_at
FROM media_encode_inbox
WHERE job_id = '<JOB_ID>'
FOR UPDATE;

UPDATE media_encode_inbox
SET status = 'RETRY_WAIT',
    lease_until = NULL,
    updated_at = UTC_TIMESTAMP(6)
WHERE job_id = '<JOB_ID>'
  AND status = 'FAILURE_PENDING';

SELECT ROW_COUNT() AS changed_rows;
COMMIT;
```

Inbox가 원래 `RETRY_WAIT`이거나 존재하지 않으면 이 UPDATE는 필요 없다. `PROCESSING`, `OUTPUT_UPLOADED`, `DONE`, `FAILED` 상태에는 실행하지 않는다.

### 4.5 단건 재발행

DLT 위치에서 같은 레코드를 다시 읽어 key와 value만 원래 토픽에 발행한다. 예외 stacktrace 같은 DLT header는 재발행하지 않는다. 메시지의 기존 `jobId`와 `requestId`를 유지한다.

```bash
kafka-console-consumer.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --topic media.encode.requested.dlt \
  --partition "$DLT_PARTITION" \
  --offset "$DLT_OFFSET" \
  --max-messages 1 \
  --property print.key=true \
  --property print.value=true \
  --property key.separator=$'\t' \
| kafka-console-producer.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --topic media.encode.requested \
  --property parse.key=true \
  --property key.separator=$'\t'
```

그 후 Worker를 다시 시작한다. 같은 메시지를 두 번 발행하더라도 Inbox가 중복 실행을 막지만, 운영자는 성공 여부가 불명확하다는 이유로 임의 재실행하지 않고 Kafka와 Worker 상태를 먼저 확인한다.

### 4.6 완료 확인

1. Worker의 `MEDIA_ENCODE_MESSAGE_CONSUMED` 로그에서 같은 `jobId`를 확인한다.
2. Inbox가 `PROCESSING`을 거쳐 `DONE` 또는 허용된 복구 상태로 전환되는지 확인한다.
3. API Job이 `DONE`인지 확인한다.
4. `media_encode_worker_dlt_total`, 인코딩 실패율과 Callback 경보가 안정화되는지 확인한다.
5. 결과와 변경 전후 상태를 변경 티켓에 기록한다.

## 5. 롤백과 에스컬레이션

- Outbox가 아직 `PENDING`이고 발행 전이라면 API Publisher를 중지한 뒤 원래 `DEAD` 상태로 되돌리는 것을 검토한다. 이미 Kafka에 발행됐다면 상태만 되돌리지 않는다.
- DLT 레코드를 원래 토픽에 발행한 뒤에는 메시지를 삭제해 롤백하지 않는다. Inbox 멱등성과 상태 전이 검증으로 결과를 확인한다.
- 같은 원인으로 두 번째 수동 재처리가 필요하면 자동 수행하지 않고 코드 결함, 외부 시스템 장애와 데이터 손상을 에스컬레이션한다.
- 범위가 여러 건이면 개별 재처리 대신 장애 원인을 수정하고 별도의 승인된 일괄 복구 도구를 작성한다.

## 6. 참고

- [Spring Kafka DLT 처리와 원본 위치 header](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Spring Kafka Retry Topic DLT 전략](https://docs.spring.io/spring-kafka/reference/retrytopic/dlt-strategies.html)

