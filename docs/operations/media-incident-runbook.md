# 미디어 인코딩 장애 대응 Runbook

- 작성일: 2026-08-31
- 적용 범위: Onfilm API, Encoding Worker, Kafka, API·Worker DB, S3 호환 스토리지, Worker Callback
- 대상 Dashboard: Grafana `Onfilm / Onfilm Media Operations`
- 관련 정책: [미디어 처리 장애 대응 정책](../decisions/media-failure-handling-policy.md)
- 상세 재처리 절차: [미디어 Outbox DEAD·Kafka DLT 재처리 절차](media-dead-letter-reprocessing.md)
- 담당: Backend 운영자

## 1. 목적

이 문서는 미디어 인코딩 장애를 발견했을 때 무엇을 먼저 확인하고, 자동 복구를 기다릴지 수동으로 개입할지 판단하고, 어떤 조건에서 복구 완료를 선언할지를 정한다.

핵심 목표는 빠른 재실행이 아니라 다음 불변 조건을 지키는 것이다.

- API Job과 발행할 Outbox가 함께 저장되어야 한다.
- 같은 `jobId`가 여러 번 전달되어도 인코딩과 영화 결과가 중복 반영되지 않아야 한다.
- 출력 업로드가 끝난 작업은 Callback 실패 때문에 FFmpeg부터 다시 실행하지 않아야 한다.
- `DONE`과 `FAILED`는 반대 최종 상태로 덮어쓰지 않아야 한다.
- 자동 복구를 소진한 항목은 Outbox `DEAD`, Worker Inbox 또는 Kafka DLT에서 추적할 수 있어야 한다.

## 2. 현재 관측 범위와 제약

현재 저장소에는 Prometheus Alert rule과 Grafana Dashboard가 구성되어 있지만 실제 Slack·이메일·PagerDuty 수신 채널은 연결되어 있지 않다. 수신 채널을 연결하기 전에는 Prometheus의 `Alerts` 화면과 Grafana의 `현재 발생 경보` 패널을 직접 확인한다.

전용 Alert가 구현된 항목은 다음과 같다.

| Alert | 조건 | 심각도 | 첫 확인 대상 |
|---|---|---|---|
| `OnfilmApiDown` | API scrape 실패 1분 | Critical | API 프로세스와 Actuator |
| `OnfilmEncodingWorkerDown` | Worker scrape 실패 1분 | Critical | Worker 프로세스와 Actuator |
| `MediaOutboxDeadRecordsDetected` | Outbox `DEAD` 1건 이상 1분 | Critical | Outbox 실패 로그와 Kafka |
| `MediaOutboxPendingTooOld` | 가장 오래된 PENDING이 2분 초과 1분 | Warning | Publisher, Kafka, lease |
| `MediaWorkerDltReceived` | 최근 5분 DLT 1건 이상 | Warning | DLT 로그와 현재 Job 상태 |
| `MediaWorkerDltBurst` | 최근 15분 DLT 5건 이상 1분 | Critical | 공통 실패 원인과 외부 시스템 |
| `MediaKafkaConsumerLagSustained` | record lag가 15분 동안 0보다 큼 | Warning | Worker 처리율과 처리 시간 |
| `MediaEncodingFailureRateHigh` | 15분 최소 10건 중 실패율 10% 초과 2분 | Warning | 실패 `stage`, `code`, `retryable` |
| `MediaEncodingFailureBurst` | 최근 5분 실패 3건 이상 | Warning | 공통 실패 단계와 오류 코드 |
| `MediaEncodingTimeoutDetected` | 최근 15분 Job timeout 1건 이상 | Warning | Job과 Worker의 처리 시간 |
| `MediaCallbackFailurePendingTooOld` | `FAILURE_PENDING` 10분 초과 1분 | Warning | Core API와 Callback 응답 |
| `OnfilmApiHighServerErrorRate` | 5분 최소 20건 중 5xx 5% 초과 2분 | Critical | `errorCode`, DB Pool, 외부 시스템 |

다음 항목은 현재 전용 Alert가 없다. 기존 Alert의 원인 조사 또는 Dashboard·Prometheus 수동 확인으로 감지한다.

- DB Connection Pool 고갈
- S3 timeout과 연결 장애
- Worker stale recovery 증가
- 중복 또는 역순 Callback 충돌 증가

## 3. 공통 초동 대응

### 3.1 최초 5분 체크리스트

1. Alert 이름, 발생 시각, `service`, `severity`, 현재 값을 장애 기록에 남긴다.
2. Grafana `Onfilm Media Operations`에서 같은 시각의 API, Outbox, Worker, Inbox 패널을 함께 본다.
3. Prometheus target에서 API와 Worker가 `UP`인지 확인한다.
4. 집계 메트릭의 실패 구간에서 구조화 로그를 조회해 `jobId`와 `correlationId`를 찾는다.
5. 같은 `correlationId`로 API → Outbox → Worker → Callback 흐름을 연결한다.
6. API Job, Outbox, Worker Inbox의 현재 상태를 읽기 전용으로 확인한다.
7. 자동 재시도 또는 lease 복구 중이면 상태를 임의로 변경하지 않는다.
8. 영향 범위가 단일 Job인지 여러 Job 또는 전체 서비스인지 판단한다.

수집 상태를 빠르게 확인하는 PromQL은 다음과 같다.

```promql
up{job=~"onfilm-.*"}
```

```promql
sum(ALERTS{alertstate="firing"}) by (alertname, severity, service)
```

### 3.2 식별자 추적 순서

구조화 로그는 다음 순서로 좁힌다.

```text
발생 시간 범위 + eventType
→ jobId 또는 requestId 발견
→ correlationId로 전체 흐름 조회
→ stage, status, attempt, retryable, errorCode 확인
```

주요 로그 필드는 다음과 같다.

- 공통: `service`, `environment`, `correlationId`, `requestId`, `jobId`, `eventType`
- API 요청: `method`, `path`, `status`, `elapsedMs`
- Outbox: `outboxId`, `status`, `attempt`, `retryable`
- Worker: `movieId`, `jobType`, `preset`, `stage`, `errorCode`, `retryable`
- DLT: `dltTopic`, `dltPartition`, `dltOffset`, `originalTopic`, `originalPartition`, `originalOffset`

로그 검색 문법은 사용하는 로그 플랫폼에 맞추되 다음 조건을 조합한다.

```text
eventType=MEDIA_ENCODE_ATTEMPT_FAILED AND jobId=<JOB_ID>
correlationId=<CORRELATION_ID>
eventType=HTTP_REQUEST_COMPLETED AND status>=500
```

토큰, HMAC 서명, Callback secret, 인증 헤더, Presigned URL과 사용자 파일 내용은 로그나 장애 기록에 복사하지 않는다.

### 3.3 상태 조회

API DB에서 Job과 Outbox를 함께 조회한다.

```sql
SELECT
    j.id AS job_id,
    j.request_id,
    j.status AS job_status,
    j.job_type,
    j.requested_at,
    j.started_at,
    j.completed_at,
    j.failure_code,
    j.failure_reason,
    o.id AS outbox_id,
    o.status AS outbox_status,
    o.attempts,
    o.next_attempt_at,
    o.lease_until,
    o.last_error
FROM media_encode_jobs j
LEFT JOIN media_encode_outbox o ON o.job_id = j.id
WHERE j.id = '<JOB_ID>';
```

Worker DB에서 Inbox 체크포인트를 조회한다.

```sql
SELECT
    job_id,
    status,
    attempts,
    lease_until,
    failure_code,
    failure_reason,
    created_at,
    updated_at
FROM media_encode_inbox
WHERE job_id = '<JOB_ID>';
```

두 DB는 별도 저장소일 수 있으므로 하나의 트랜잭션으로 변경하지 않는다. 초동 조사에서는 읽기 전용 조회만 수행한다.

### 3.4 수동 개입 공통 금지 사항

- 원인 확인 전에 Outbox `DEAD` 또는 Kafka DLT를 재투입하지 않는다.
- `DONE` 또는 `FAILED`인 API Job과 Worker Inbox를 이전 상태로 되돌리지 않는다.
- `PROCESSING` lease가 유효한 작업을 다른 Worker가 다시 처리하도록 만들지 않는다.
- `OUTPUT_UPLOADED` 작업을 원본 인코딩부터 재실행하지 않는다.
- Outbox나 Inbox 전체를 대상으로 대량 `UPDATE`하지 않는다.
- Kafka 발행 성공 여부가 불명확하다는 이유만으로 동일 메시지를 반복 발행하지 않는다.
- DB Pool 크기, timeout, 재시도 횟수를 원인 분석 없이 크게 늘리지 않는다.

## 4. 서비스 가용성 장애

### 4.1 API Down

**증상과 영향**

- `OnfilmApiDown`이 발생한다.
- 사용자 API 요청과 Worker Callback이 실패한다.
- Worker는 출력 업로드 후 `OUTPUT_UPLOADED` 또는 실패 보고 전 `FAILURE_PENDING`에 머물 수 있다.

**확인**

1. `Scrape Target 상태` 패널과 `up{job="onfilm-api"}`를 확인한다.
2. 운영 환경의 API health endpoint와 프로세스 상태를 확인한다.
3. 종료 직전 `HTTP_REQUEST_COMPLETED`, 예외, OOM, DB 연결 오류 로그를 확인한다.
4. Worker의 `MEDIA_ENCODE_FAILURE_CALLBACK_FAILED`와 Callback `result=retry` 증가를 확인한다.

**복구**

- 배포 플랫폼의 표준 절차로 API 인스턴스를 복구한다.
- 설정 오류나 DB·외부 시스템 장애가 원인이면 원인을 먼저 제거한다.
- API 복구 직후 Callback이나 DLT를 수동 재발행하지 않는다. Worker의 Callback 재시도와 `FAILURE_PENDING` 감소를 먼저 확인한다.

**정상화 기준**

- API target이 `UP`으로 5분 이상 유지된다.
- 신규 HTTP 5xx가 정상 범위로 감소한다.
- Worker Callback 성공이 발생하고 `FAILURE_PENDING` oldest age가 감소한다.

### 4.2 Worker Down

**증상과 영향**

- `OnfilmEncodingWorkerDown`이 발생한다.
- Kafka Consumer Lag가 증가하고 신규 인코딩이 시작되지 않는다.
- 처리 중이던 Inbox는 lease 만료 후 복구 대상이 된다.

**확인**

1. `up{job="onfilm-encoding-worker"}`와 Worker health endpoint를 확인한다.
2. 종료 직전 `MEDIA_ENCODE_ATTEMPT_FAILED`, OOM, 디스크 부족, FFmpeg 프로세스 오류를 확인한다.
3. Kafka Consumer group과 Inbox `PROCESSING`, `OUTPUT_UPLOADED` 상태를 확인한다.

```bash
kafka-consumer-groups.sh \
  --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --group onfilm-encoding-worker \
  --describe
```

**복구**

- 원인을 제거한 뒤 표준 배포 절차로 Worker를 복구한다.
- 유효한 lease를 DB에서 임의로 만료시키지 않는다.
- Worker 재기동 후 Kafka 재전달과 stale recovery가 자동 복구하도록 둔다.
- `OUTPUT_UPLOADED`는 Callback-only로 복구되는지 확인한다.

**정상화 기준**

- Worker target이 `UP`으로 5분 이상 유지된다.
- Consumer Lag가 지속적으로 감소한다.
- stale recovery 실패가 증가하지 않고 Inbox가 `DONE` 또는 허용된 재시도 상태로 이동한다.

## 5. Outbox 적체와 DEAD

### 5.1 PENDING 적체

**탐지**

- Alert: `MediaOutboxPendingTooOld`
- Dashboard: `Outbox 상태별 현재 건수`, `가장 오래된 Outbox PENDING`, `Outbox 발행·재시도 결과`
- 로그: `MEDIA_ENCODE_OUTBOX_PUBLISH_FAILED`

**예상 원인**

- Kafka broker 연결 실패 또는 응답 timeout
- Publisher가 실행되지 않거나 API 인스턴스가 불안정함
- Outbox가 `PUBLISHING`인 상태에서 프로세스가 종료되어 2분 lease 만료를 기다리는 중
- 잘못된 payload 또는 지원하지 않는 schema version

**조치**

1. API와 Kafka 상태, Publisher 실패 로그의 공통 예외를 확인한다.
2. `PENDING`과 `PUBLISHING` 건수, `attempts`, `next_attempt_at`, `lease_until`을 확인한다.
3. Kafka 장애라면 broker를 복구하고 Outbox의 자동 재발행을 기다린다.
4. `PUBLISHING` lease가 유효하면 상태를 변경하지 않는다.
5. 동일 원인이 다수 Job에서 발생하면 개별 재처리를 중단하고 시스템 장애로 대응한다.

**정상화 기준**

- oldest PENDING age가 2분 아래로 내려간다.
- `MEDIA_ENCODE_OUTBOX_PUBLISHED`가 다시 발생한다.
- Outbox `PUBLISHED`가 증가하고 새 `DEAD`가 생기지 않는다.

### 5.2 DEAD 발생

**탐지**

- Alert: `MediaOutboxDeadRecordsDetected`
- 로그: `MEDIA_ENCODE_OUTBOX_DEAD`
- 상태: 발행 8회 실패 후 Outbox `DEAD`

**조치**

1. `outboxId`, `jobId`, `attempt`, `last_error`와 API Job 상태를 기록한다.
2. Kafka 발행 성공 후 상태 기록만 실패했을 가능성이 있으므로 Worker Inbox와 소비 로그를 먼저 확인한다.
3. 원인을 제거해도 자동으로 `DEAD`를 재발행하지 않는다.
4. [Outbox DEAD 재처리 허용 조건](media-dead-letter-reprocessing.md#31-허용-조건)을 모두 만족할 때만 한 건씩 재처리한다.

**정상화 기준**

- 원인이 제거되고 새 `DEAD`가 발생하지 않는다.
- 승인된 대상은 `PUBLISHED`로 전환되고 연결된 Job이 정상 처리된다.
- 작업자, 사유, 변경 전후 상태와 검증 결과가 감사 기록에 남는다.

## 6. Kafka Consumer Lag와 DLT

### 6.1 Consumer Lag 지속

**탐지**

- Alert: `MediaKafkaConsumerLagSustained`
- Dashboard: `Worker 인코딩 시도율`, `Worker 단계별 처리 시간 p95`, `Worker Inbox 점유 결과`

**예상 원인**

- Worker 중단 또는 처리량 부족
- FFmpeg, S3 download/upload, Callback 중 한 단계의 처리 시간 증가
- 같은 작업의 `BUSY` 재전달 반복
- DB 또는 로컬 디스크 병목

**조치**

1. Worker `UP`, Consumer group lag와 현재 처리율을 확인한다.
2. `Worker 단계별 처리 시간 p95`에서 느려진 단계를 찾는다.
3. `Worker 단계·원인별 실패`와 `MEDIA_ENCODE_ATTEMPT_FAILED`를 함께 확인한다.
4. `BUSY`가 많으면 처리 중인 lease와 실제 Worker 생존 여부를 확인한다.
5. 원인 제거 전에 Worker concurrency나 인스턴스를 무조건 늘리지 않는다. 현재 Inbox 공유 범위와 스토리지·DB 수용량을 먼저 확인한다.

**정상화 기준**

- Consumer Lag가 연속적으로 감소해 0 또는 정상 범위에 도달한다.
- 단계별 p95와 실패율이 정상화된다.
- DLT 유입이 새로 발생하지 않는다.

### 6.2 DLT 유입

**탐지**

- Alert: `MediaWorkerDltReceived`, `MediaWorkerDltBurst`
- Dashboard: `Worker stale recovery·DLT`
- 로그: `MEDIA_ENCODE_DLT_RECEIVED`, `MEDIA_ENCODE_DLT_INVALID_MESSAGE`

**조치**

1. DLT의 `jobId`, 원본 topic·partition·offset, `failureType`, 정제된 `failureMessage`를 기록한다.
2. 동일 `stage`와 `errorCode`가 반복되는지 확인한다.
3. `MediaWorkerDltBurst`이면 개별 재처리를 중단하고 공통 원인을 먼저 제거한다.
4. API Job과 Worker Inbox 상태를 조회한다.
5. [DLT 상태별 결정표](media-dead-letter-reprocessing.md#41-상태별-결정)에 따라 단건 재처리 가능 여부를 판단한다.

**정상화 기준**

- 원인 제거 후 DLT 추가 유입이 멈춘다.
- 승인된 단건 재처리는 기존 `jobId`로 한 번만 수행되고 최종 상태가 일치한다.
- 이미 `DONE` 또는 `FAILED`인 Job을 재실행하지 않는다.

## 7. Worker 인코딩 실패와 timeout

### 7.1 실패율 또는 실패 집중

**탐지**

- Alert: `MediaEncodingFailureRateHigh`, `MediaEncodingFailureBurst`
- Dashboard: `Worker 단계·원인별 실패`, `Worker 단계별 처리 시간 p95`
- 로그: `MEDIA_ENCODE_ATTEMPT_FAILED`

**진단 기준**

| `stage` | 대표 `errorCode` | 우선 확인 |
|---|---|---|
| `VALIDATION` | `INVALID_REQUEST`, `UNSUPPORTED_MESSAGE_SCHEMA` | payload 계약과 Worker 버전 |
| `DOWNLOAD` | `SOURCE_NOT_FOUND`, `SOURCE_DOWNLOAD_FAILED` | source bucket/key와 S3 상태 |
| `PROBE` | `UNSUPPORTED_MEDIA` | 파일 손상, 형식, 크기와 재생 시간 |
| `TRANSCODE` | `ENCODE_TIMEOUT`, `ENCODE_FAILED` | FFmpeg 자원, 입력 특성, timeout |
| `UPLOAD` | `OUTPUT_UPLOAD_FAILED` | S3 쓰기 권한, timeout, 부분 업로드 |
| `CALLBACK` | `CORE_API_UNAVAILABLE`, `INVALID_REQUEST` | API 상태, 인증, 상태 충돌 |

**조치**

1. `retryable=true`와 `false`를 구분한다.
2. 여러 Job이 같은 단계에서 실패하면 외부 시스템 또는 배포 변경을 우선 조사한다.
3. 영구 검증 오류는 재시도하지 않고 입력·메시지 계약을 수정한다.
4. 일시적 오류는 Retry Topic과 Inbox 상태가 자동 복구하도록 둔다.
5. 재시도 소진 후 DLT에 도달하면 6.2 절차를 따른다.

### 7.2 Job timeout

**탐지**

- Alert: `MediaEncodingTimeoutDetected`
- API 기본 Job timeout: 4시간 30분
- Worker FFmpeg timeout: 2시간
- Worker processing lease: 3시간

**조치**

1. API Job의 `requested_at`, `started_at`, `completed_at`, `failure_code`를 확인한다.
2. Worker Inbox와 `TRANSCODE` 처리 시간, Worker 중단 시간을 확인한다.
3. API Job이 `FAILED/ENCODE_TIMEOUT`이면 같은 `jobId`를 다시 실행하거나 `REQUESTED`로 되돌리지 않는다.
4. 늦게 도착한 `DONE` Callback의 409 충돌은 기존 최종 상태를 지키는 정상 방어인지 확인한다.
5. 재처리가 필요하면 원인을 제거한 뒤 새 업로드 요청과 새 Job으로 시작한다.
6. 정상 작업이 반복해서 timeout된다면 파일 특성과 실제 처리 시간 분포를 근거로 timeout 관계를 재검토한다.

**정상화 기준**

- 신규 timeout이 발생하지 않는다.
- Worker 처리 시간 p95가 정책 범위로 복귀한다.
- timeout Job과 Movie 결과 상태가 모순되지 않는다.

## 8. Worker lease 만료와 stale recovery

**탐지**

현재 전용 Alert는 없다.

- Dashboard: `Worker stale recovery·DLT`, `Worker Inbox 상태별 현재 건수`
- 로그: `MEDIA_ENCODE_STALE_RECOVERY_STARTED`, `MEDIA_ENCODE_STALE_RECOVERY_FAILED`
- 메트릭: `media_encode_worker_stale_recovery_total{result="started|success|failure"}`

**예상 원인**

- Worker가 처리 도중 종료됨
- 처리 시간이 3시간 processing lease를 초과함
- Callback 직전 종료되어 `OUTPUT_UPLOADED` lease가 만료됨
- DB 또는 Worker 시간 설정 불일치

**조치**

1. Inbox `status`, `attempts`, `lease_until`, `updated_at`을 확인한다.
2. `PROCESSING` 복구인지 `OUTPUT_UPLOADED` 복구인지 구분한다.
3. `OUTPUT_UPLOADED`면 Callback-only로 진행되는지 확인하고 인코딩을 다시 시작하지 않는다.
4. 실제 처리 중인 Worker가 있는데 lease가 먼저 만료됐다면 중복 처리 위험이 있으므로 추가 Worker 투입을 중단하고 timeout·lease 관계를 확인한다.
5. stale recovery 실패 로그의 `jobId`로 원래 실패 단계와 DLT 여부를 추적한다.

**정상화 기준**

- stale recovery `success`가 발생하고 `failure` 증가가 멈춘다.
- 만료된 `PROCESSING`, `OUTPUT_UPLOADED` Inbox가 계속 쌓이지 않는다.
- 동일 `jobId` 결과가 중복 생성되지 않는다.

## 9. Callback 적체와 상태 충돌

### 9.1 FAILURE_PENDING 적체

**탐지**

- Alert: `MediaCallbackFailurePendingTooOld`
- Dashboard: `가장 오래된 FAILURE_PENDING`, `Worker Callback 처리 결과`, `API Callback 처리 결과`
- 로그: `MEDIA_ENCODE_FAILURE_CALLBACK_FAILED`, `MEDIA_ENCODE_FAILURE_CALLBACK_REJECTED`

**조치**

1. API가 `UP`인지, HTTP 5xx가 증가했는지 확인한다.
2. Callback 실패가 연결 timeout·5xx·429인지 인증·검증 4xx인지 구분한다.
3. HMAC secret과 시스템 시간 설정이 API와 Worker에서 일치하는지 확인하되 secret 원문을 로그에 남기지 않는다.
4. 일시적 장애면 Worker의 실패 Callback 재시도를 기다린다.
5. 영구 4xx면 API Job과 Inbox 상태, Callback payload 계약을 조사한다.
6. `OUTPUT_UPLOADED` 작업은 완료 Callback만 복구하고 FFmpeg를 다시 실행하지 않는다.

**정상화 기준**

- oldest `FAILURE_PENDING` age가 10분 아래로 내려가고 건수가 감소한다.
- Worker Callback `result=success`가 발생한다.
- API Job과 Worker Inbox의 최종 상태가 일치한다.

### 9.2 중복·역순 Callback

현재 전용 Alert는 없다.

- 메트릭: `media_encode_callback_total{result="duplicate|conflict"}`
- Dashboard: `API Callback 처리 결과`
- 중복 Callback: 같은 최종 상태 요청을 2xx no-op으로 처리
- 역순 Callback: `DONE → FAILED`, `FAILED → DONE`을 409로 거부

**조치**

1. `duplicate`가 간헐적으로 발생하고 최종 상태가 일치하면 멱등 방어가 동작한 것으로 기록한다.
2. `conflict`가 발생하면 먼저 도착한 Callback, Worker Inbox, API Job과 Movie 결과를 확인한다.
3. 기존 최종 상태를 덮어쓰지 않는다.
4. 반복 충돌이면 timeout 후 늦은 Worker 완료, DLT 중복 재처리 또는 Worker 상태 관리 결함을 조사한다.

**정상화 기준**

- API Job의 최종 상태가 유지된다.
- Movie 결과가 한 번만 반영된다.
- 동일 원인의 `conflict` 증가가 멈춘다.

## 10. API 5xx와 DB Connection Pool

### 10.1 API 5xx 증가

**탐지**

- Alert: `OnfilmApiHighServerErrorRate`
- 로그: `HTTP_REQUEST_COMPLETED`의 `status=5xx`, 같은 `correlationId`의 예외

**조치**

1. 실패 `path`, `errorCode`, 응답 시간과 배포 시점을 분류한다.
2. 모든 API인지 미디어 Callback·업로드 경로만인지 확인한다.
3. DB Pool, DB 연결, Kafka, S3 같은 의존 시스템 상태를 확인한다.
4. 데이터 검증 4xx와 서버 5xx를 섞어 계산하지 않는다.
5. 복구 후 동일 요청을 재시도할 때 멱등 키와 현재 Job 상태를 먼저 확인한다.

### 10.2 DB Connection Pool 고갈

현재 전용 Alert와 Grafana 패널은 없다. `OnfilmApiHighServerErrorRate`, 응답 시간 증가와 다음 PromQL로 수동 확인한다.

```promql
hikaricp_connections_active / clamp_min(hikaricp_connections_max, 1)
```

```promql
hikaricp_connections_pending
```

```promql
increase(hikaricp_connections_timeout_total[5m])
```

**예상 원인**

- 느린 쿼리 또는 장시간 트랜잭션
- DB 장애나 커넥션 획득 지연
- 트래픽 급증
- 외부 I/O를 포함한 잘못된 트랜잭션 경계의 신규 코드

**조치**

1. active/max 비율, pending, timeout 증가와 DB 상태를 확인한다.
2. 장시간 실행 쿼리와 트랜잭션을 찾고 관련 API 경로를 연결한다.
3. 쿼리 취소나 세션 종료는 영향 범위를 확인하고 DBA 또는 운영 승인 후 수행한다.
4. Pool 크기만 늘리거나 API를 반복 재시작하지 않는다. DB가 수용할 수 있는 연결 수와 근본 원인을 먼저 확인한다.
5. 최근 변경에서 Kafka·S3·FFmpeg·BCrypt 같은 외부 작업이 트랜잭션 안으로 들어갔는지 확인한다.

**정상화 기준**

- pending이 0 또는 정상 범위로 복귀한다.
- connection timeout 증가가 멈춘다.
- API 5xx와 응답 시간이 정상화된다.
- 원인이 된 느린 쿼리나 장시간 트랜잭션이 해소된다.

## 11. S3 timeout과 스토리지 장애

현재 전용 Alert는 없다. Worker 실패율과 단계별 오류로 감지한다.

- `DOWNLOAD + SOURCE_DOWNLOAD_FAILED`: 원본 조회·다운로드 일시 실패
- `DOWNLOAD + SOURCE_NOT_FOUND`: 원본 없음 또는 영구 접근 실패
- `UPLOAD + OUTPUT_UPLOAD_FAILED`: 결과 업로드 실패
- `retryable=true`: timeout, 408, 429, 5xx 등 일시 오류
- `retryable=false`: 재시도로 해결되지 않는 입력·권한·파일 오류

**확인**

1. 같은 시간대 여러 Job과 bucket에서 발생하는지 확인한다.
2. Worker의 S3 연결, 자격 증명, bucket 정책, 네트워크와 SDK timeout을 확인한다.
3. 장애 기록에는 bucket과 필요한 최소 key만 남기고 Presigned URL과 인증 정보는 남기지 않는다.
4. 원본 존재를 읽기 전용으로 확인한다.

```bash
aws s3api head-object \
  --bucket "$SOURCE_BUCKET" \
  --key "$SOURCE_KEY"
```

**복구**

- 일시 장애는 Worker 재시도 정책을 사용한다.
- `SOURCE_NOT_FOUND`는 자동 재시도하지 않고 업로드 완료 여부와 source key 소유권을 확인한다.
- 업로드 실패는 target prefix에 부분 파일이 남았는지 확인한다. Worker는 실패 시 이번 시도에서 업로드한 객체를 best-effort로 삭제하지만 삭제 실패 가능성을 고려한다.
- HLS manifest가 없는 부분 결과는 완료 결과로 간주하지 않는다.
- `OUTPUT_UPLOADED` 이후 Callback 장애는 스토리지 작업을 반복하지 않는다.

**정상화 기준**

- S3 읽기·쓰기가 정상 응답한다.
- download/upload 단계 실패율이 정상화된다.
- 완료 Job의 target manifest와 DB 결과 key가 일치한다.
- 실패 Job의 부분 결과가 노출되지 않는다.

## 12. 복구 완료 공통 체크리스트

장애 종료 전에 다음을 모두 확인한다.

- 관련 Critical·Warning Alert가 해제되었다.
- API와 Worker target이 `UP`이다.
- Outbox oldest PENDING age와 `DEAD`가 증가하지 않는다.
- Kafka Consumer Lag가 감소하거나 정상 범위다.
- DLT 추가 유입이 멈췄다.
- Worker 인코딩 실패율과 단계별 처리 시간이 정상 범위다.
- stale recovery 실패가 증가하지 않는다.
- `FAILURE_PENDING` oldest age가 감소한다.
- API 5xx와 DB Connection Pool이 정상화됐다.
- 표본 `jobId`의 API Job, Outbox, Inbox, Movie 결과가 일치한다.
- 수동 변경이 있었다면 작업자, 사유, 대상, 변경 전후 상태와 결과를 기록했다.

장애가 잠시 사라졌더라도 같은 원인이 반복되거나 데이터 상태가 불일치하면 종료하지 않고 에스컬레이션한다.

## 13. 장애 기록과 사후 조치

장애 기록에는 다음 정보를 남긴다.

```text
incidentId, detectedAt, resolvedAt, severity,
alertName, affectedService, impact,
correlationId, requestId, jobId, outboxId,
dltTopic, dltPartition, dltOffset,
timeline, rootCause, recoveryAction,
beforeStatus, afterStatus, verificationResult,
operator, ticketId, followUpActions
```

고유 식별자는 장애 조사와 감사 기록에만 사용하고 Prometheus 메트릭 태그로 추가하지 않는다.

사후 조치는 다음 순서로 정리한다.

1. 탐지되지 않은 장애라면 새 Alert 또는 Dashboard 패널 필요성을 검토한다.
2. 자동 복구가 실패한 이유와 재시도·timeout·lease 정책의 적정성을 검토한다.
3. 같은 장애를 재현하는 단위 또는 `integrationTest`를 추가한다.
4. 복구 과정에서 수동 SQL이나 임시 명령이 필요했다면 조건부 운영 도구로 만들지 평가한다.
5. 정책이 변경되면 이 Runbook, 장애 대응 정책, Alert rule과 테스트를 함께 갱신한다.

현재 자동화된 장애 주입 테스트는 API와 Worker 저장소에서 다음 명령으로 실행한다.

```bash
./gradlew integrationTest
```

실제 Kafka 네트워크 단절, S3 지연과 OS 수준 Worker 종료 훈련은 운영과 분리된 환경에서 수행하고 결과를 장애 기록 형식으로 남긴다.
