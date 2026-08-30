# 미디어 파이프라인의 장애 대응과 관측성 구축

- 작업일: 2026-08-30~2026-08-31
- 문서 작성일: 2026-08-31
- 관련 API 커밋: `1959c0f`, `977b718`, `7ba31cb`, `d6cb369`, `d26f525`, `67c88f1`, `1f7b87c`, `f6c3400`, `ff4a44c`, `0b16e09`, `1084835`
- 관련 Worker 커밋: `f8504b6`, `bfe7c77`, `74e6b16`, `93f13c6`, `2d32e35`
- 상태: 장애 대응 구현·자동 검증·운영 문서화 완료. 실제 알림 채널 연결과 외부 시스템 장애 훈련은 후속 과제

## 문제

Transactional Outbox와 Worker Inbox를 도입해 DB와 Kafka 사이의 메시지 유실, 중복 메시지와 중복 Callback을 방어하고 있었지만, 장애가 실제로 발생했을 때 다음 질문에 일관되게 답하기 어려웠다.

- DB 커밋 후 Kafka 발행 전에 API가 종료되면 요청이 어디에 남는가?
- Kafka 발행 성공 후 Outbox 상태 저장 전에 종료되면 중복 발행을 어떻게 확인하는가?
- Worker가 FFmpeg 실행 중 종료되면 어느 시점부터 복구되는가?
- 출력 업로드 후 Callback만 실패했을 때 인코딩이 다시 실행되지 않는가?
- `DONE`과 `FAILED` Callback이 중복되거나 역순으로 도착하면 어떤 상태가 유지되는가?
- Outbox `DEAD`와 Kafka DLT는 각각 무엇을 의미하며 어떤 조건에서 다시 처리할 수 있는가?
- 장애가 발생하면 어떤 Dashboard, 로그, 메트릭과 DB 상태를 먼저 확인해야 하는가?

신뢰성 로직은 존재했지만 이를 관측하고 운영하는 체계가 부족했다.

- API 요청부터 Outbox, Kafka, Worker, Callback까지 하나의 흐름으로 연결할 식별자가 없었다.
- Outbox 적체, Worker 실패 단계, Inbox 상태, Callback 충돌과 DLT를 나타내는 운영 메트릭이 부족했다.
- API와 Worker의 처리 시간 제한 관계가 명확하지 않아 정상 장기 작업을 API가 먼저 timeout 처리할 가능성이 있었다.
- Dashboard와 Alert가 없어 장애를 로그를 직접 뒤져 발견해야 했다.
- 자동 재시도를 소진한 `DEAD`와 DLT의 재처리 조건이 명문화되지 않아 무조건 재발행하면 최종 상태를 덮어쓰거나 중복 인코딩할 위험이 있었다.
- 정상 경로 단위 테스트만으로는 프로세스 종료, lease 만료, 반복 실패와 역순 Callback 뒤의 영속 상태를 입증하기 어려웠다.

## 원인

장애 대응이 개별 클래스의 재시도와 예외 처리 수준에 머물렀고, 분산 처리 전체의 공통 계약으로 연결되지 않은 것이 원인이었다.

### 실패 분류와 상태 정책의 분산

Kafka, S3, FFmpeg, Callback과 DB는 실패 의미와 복구 지점이 서로 다르다. 그런데 일시 오류와 영구 오류, 전체 인코딩 재실행과 Callback-only 재시도, 자동 복구와 수동 복구의 경계가 하나의 정책으로 정리되지 않았다.

### 운영 식별자와 메트릭 역할의 혼동

`jobId`, `requestId`, `correlationId`는 개별 요청을 추적하기에는 유용하지만 Prometheus Tag로 사용하면 요청마다 새로운 시계열이 만들어진다. 반대로 저카디널리티 메트릭만 사용하면 특정 실패 Job을 찾을 수 없으므로 집계 탐지와 개별 추적의 역할을 나눌 필요가 있었다.

### 코드 상태와 운영 절차의 단절

Outbox `DEAD`, Worker `FAILURE_PENDING`, `OUTPUT_UPLOADED`, Kafka DLT처럼 복구 판단에 필요한 상태는 코드와 DB에 존재했다. 하지만 운영자가 어떤 상태 조합에서 재처리해야 하는지, 어떤 경우에는 절대 재처리하면 안 되는지가 문서와 검증 절차로 연결되지 않았다.

### 테스트 경계의 부족

Mock 기반 단위 테스트는 개별 분기를 빠르게 확인하지만 트랜잭션 커밋·롤백 후 DB에 남는 상태와 여러 서비스 호출에 걸친 lease 복구를 충분히 보여주지 못했다. 반대로 모든 테스트를 실제 Kafka·MySQL·S3 프로세스로 구성하면 실행 비용과 불안정성이 커지므로 테스트 계층을 분리할 필요가 있었다.

## 해결

먼저 [미디어 처리 장애 대응 정책](../decisions/media-failure-handling-policy.md)에 실패 유형, 재시도 가능 여부, timeout, 상태 전이와 수동 복구 기준을 단일 정책으로 정리했다.

### 1. 실패 유형과 재시도 경계 정의

- 연결 실패, timeout, HTTP 429·5xx와 throttling은 제한적으로 재시도
- 검증 실패, 인증 실패, 지원하지 않는 미디어와 최종 상태 충돌은 재시도하지 않음
- 재시도 대기 중 스레드나 DB 커넥션을 점유하지 않고 다음 실행 시각과 상태를 DB 또는 Retry Topic에 기록
- Outbox는 최대 8회 지수 백오프 후 `DEAD`로 격리
- Worker 출력 업로드 전 장애는 인코딩 복구, 업로드 후 장애는 Callback-only 복구
- `DONE`과 `FAILED`는 최종 상태로 두고 반대 상태 Callback을 409로 거부
- Circuit Breaker는 초기 필수 구성에서 제외하고 timeout, 제한된 재시도, Outbox와 상태 전이 검증을 우선 적용

시간 제한은 다음 목표 관계로 정리하고 API Job timeout을 4시간 30분으로 조정했다.

```text
FFmpeg timeout 2시간
    < Inbox lease 3시간
    < Kafka max.poll.interval 목표 4시간
    < API Job timeout 4시간 30분
```

Kafka `max.poll.interval`의 4시간 조정은 아직 후속 과제로 남겨 현재 구현과 목표값을 구분했다.

### 2. correlationId 전파와 구조화 로그

API가 유효한 `X-Correlation-Id`를 수용하고 없거나 형식이 잘못되면 UUID를 생성하도록 했다. 이 값을 응답 헤더, Outbox payload, Kafka 메시지, Worker MDC와 내부 Callback 헤더까지 전달했다.

```text
HTTP 요청
→ API Job·Outbox
→ Kafka 메시지
→ Worker 처리·stale recovery
→ 내부 Callback
```

기존 메시지와의 호환성을 위해 `correlationId`가 없는 경우 `requestId`를 대체값으로 사용하고 선택 필드 추가만으로 schema version을 올리지 않았다. 운영 프로필은 JSON 구조화 로그를 사용하고 작업 범위가 끝나면 MDC를 해제해 스레드 재사용 시 식별자가 섞이지 않도록 했다.

로그에는 `eventType`, `jobId`, `requestId`, `correlationId`, `stage`, `status`, `attempt`, `retryable`, `errorCode`를 남겼다. 토큰, HMAC 서명, Callback secret, Presigned URL과 인증 헤더는 기록하지 않았다.

### 3. API와 Worker 운영 메트릭

API에는 다음 관측 지점을 추가했다.

- Outbox 발행 성공·실패, 재시도 예약·`DEAD`
- Outbox 상태별 건수와 가장 오래된 PENDING 체류 시간
- Callback 적용·중복·충돌·오류와 처리 시간
- Job 생성, 상태 전이, 성공·실패·timeout과 전체 처리 시간

Worker에는 다음 관측 지점을 추가했다.

- 인코딩 전체 시도와 성공·실패
- validation, download, probe, transcode, upload, callback 단계별 처리 시간
- 단계·내부 오류 코드·재시도 가능 여부별 실패
- Inbox `PROCESS`, `BUSY`, `CALLBACK_ONLY`, `TERMINAL` 점유 결과
- Inbox 상태별 건수, `FAILURE_PENDING` oldest age
- stale recovery, 완료·실패 Callback과 DLT 결과

현재 상태 Gauge를 Prometheus scrape마다 DB에서 직접 조회하지 않았다. API와 Worker가 기본 30초마다 DB 상태를 읽어 메모리 스냅샷을 갱신하고 scrape는 이 값을 반환하도록 해 수집 주기와 DB 부하를 분리했다.

`jobId`, `movieId`, `userId`, `requestId`, `correlationId`는 메트릭 Tag에서 제외했다. 메트릭은 `type`, `stage`, enum 기반 `code`, `status`, `result`, `retryable`처럼 값의 범위가 제한된 Tag만 사용하고 개별 Job 추적은 구조화 로그가 담당하도록 했다.

### 4. Actuator와 Prometheus 노출 경계

API와 Worker에 Prometheus Registry를 적용하고 Actuator는 `health`, `info`, `prometheus`만 노출했다. Health 상세 구성 정보는 숨기고 운영 API 관리 포트는 기본 `127.0.0.1:8081`에 바인딩해 애플리케이션 포트와 분리했다. Worker의 기본 포트는 `8082`로 통일했다.

공통 메트릭 Tag는 `application`, `environment`로 제한했다. 운영 Actuator는 인터넷에 직접 공개하지 않고 사설망과 방화벽 안의 Prometheus만 접근한다는 정책을 문서화했다.

### 5. Prometheus·Grafana와 Alert

로컬에서 재현 가능한 모니터링 구성을 `infra/monitoring`에 추가했다.

- Prometheus가 15초마다 API와 Worker의 `/actuator/prometheus` 수집
- Grafana datasource와 `Onfilm Media Operations` Dashboard 자동 provisioning
- Job, Outbox, Callback, Worker 단계, Inbox, stale recovery와 DLT를 포함한 17개 이상 패널
- 컨테이너 이미지를 고정하고 Prometheus·Grafana 포트를 `127.0.0.1`에만 바인딩
- `.env`는 제외하고 비밀값이 없는 `.env.example`만 관리

초기 Alert는 다음 장애를 대상으로 구성했다.

- API·Worker Down
- Outbox PENDING 2분 초과와 `DEAD` 발생
- Kafka DLT 유입·반복과 Consumer Lag 15분 지속
- 최근 15분 최소 10건 중 인코딩 실패율 10% 초과
- 최근 5분 인코딩 실패 3건 이상
- 인코딩 Job timeout 발생
- `FAILURE_PENDING` 10분 초과
- 최근 5분 최소 20건 중 API 5xx 비율 5% 초과

소량 트래픽의 한 건 실패가 비율을 과대평가하지 않도록 최소 표본 조건을 함께 적용했다. 현재 Kafka 기본 메트릭으로 oldest record age를 알 수 없어 Consumer Lag가 15분간 0으로 복구되지 않는 조건으로 근사하고, “3건 연속 실패” 대신 “5분 내 3건 실패”를 사용한다는 한계도 문서에 남겼다.

### 6. Outbox DEAD와 Kafka DLT의 수동 복구 분리

[미디어 Outbox DEAD·Kafka DLT 재처리 절차](../operations/media-dead-letter-reprocessing.md)에 두 실패 지점의 차이와 단건 복구 조건을 작성했다.

- Outbox `DEAD`: API에서 Kafka로 발행하지 못한 요청
- Kafka DLT: Worker가 메시지를 받았지만 자동 처리를 완료하지 못한 요청

자동 재투입과 대량 `UPDATE`를 금지하고 API Job, Worker Inbox, 원본 파일, payload의 `jobId`와 schema version을 확인한 뒤 한 건씩 처리하도록 했다. 이미 `DONE` 또는 `FAILED`인 Job과 Inbox는 되돌리지 않고, DLT는 최소 14일 보존하며 작업자·시각·사유·원본 topic·partition·offset을 감사 기록에 남긴다.

### 7. 장애 주입 통합 테스트 분리

일반 단위 테스트와 별도로 `integrationTest` Gradle source set을 API와 Worker에 추가하고 `check`에 연결했다. 실제 JPA 트랜잭션과 H2 DB 상태를 사용하되 테스트용 `Clock`을 이동시켜 lease와 backoff 시간만큼 실제로 기다리지 않고 다음 시나리오를 검증했다.

API 통합 테스트:

- Outbox 점유 직후 Publisher 종료를 가정한 lease 만료 전 재점유 금지와 만료 후 복구
- Kafka 발행 8회 실패 후 `DEAD`와 마지막 오류 보존
- Job·Outbox 저장 중 예외 발생 시 동시 롤백
- 중복 `DONE` Callback의 Movie 결과 단일 반영
- `DONE → FAILED`, `FAILED → DONE` 역순 Callback 거부와 기존 최종 상태 유지

Worker 통합 테스트:

- 같은 Kafka 메시지 중복 수신 시 `BUSY`, lease 만료 후 `PROCESS` 복구
- 출력 업로드 후 Callback timeout 시 `CALLBACK_ONLY`로 재개
- 동일 `jobId`의 서로 다른 payload를 영구 계약 위반으로 거부
- 영구 실패 후 `FAILED` 상태와 실패 원인 보존, 중복 메시지 `TERMINAL` 처리

### 8. 장애 대응 Runbook

마지막으로 [미디어 인코딩 장애 대응 Runbook](../operations/media-incident-runbook.md)에 다음 운영 순서를 연결했다.

```text
Alert와 영향 확인
→ Dashboard에서 실패 구간 식별
→ eventType과 correlationId로 로그 추적
→ API Job·Outbox·Worker Inbox 상태 조회
→ 자동 복구 또는 수동 개입 판단
→ 정상화 기준 확인
→ 장애 기록과 재발 방지 작업 등록
```

API·Worker Down, Outbox 적체·`DEAD`, Kafka Lag·DLT, 인코딩 실패·timeout, lease 만료, Callback 적체·충돌, API 5xx, DB Pool과 S3 장애를 다뤘다. 전용 Alert가 없는 DB Pool, S3, stale recovery와 Callback 충돌은 자동 탐지된 것처럼 표현하지 않고 기존 Alert의 2차 진단 또는 Prometheus 수동 확인 항목으로 구분했다.

## 기술 선택과 트레이드오프

### 선택한 방법

#### 메트릭으로 탐지하고 로그로 개별 요청 추적

Prometheus는 제한된 Tag로 비율·건수·지연을 집계하고, 특정 `jobId`와 `correlationId`는 구조화 로그에서 찾도록 역할을 분리했다. 시계열 폭증을 막으면서도 분산 처리 흐름을 추적할 수 있다.

#### DB 스냅샷 기반 Gauge

Gauge scrape마다 DB를 조회하는 대신 30초 주기의 상태 스냅샷을 선택했다. Prometheus 수집 장애가 DB 부하로 전파되는 것을 줄이는 대신 최대 한 주기만큼 값이 늦을 수 있다.

#### 결정적인 DB 통합 테스트와 실제 장애 훈련 분리

핵심 불변 조건은 H2와 테스트용 Clock으로 빠르고 반복 가능하게 검증했다. Kafka 네트워크 단절, S3 지연과 OS 수준 Worker 종료는 테스트가 느리고 환경 의존적이므로 Runbook 기반의 분리된 장애 훈련으로 남겼다.

#### 상태 기반 단건 수동 재처리

`DEAD`와 DLT를 자동 재투입하지 않고 현재 Job·Inbox 상태를 확인한 뒤 한 건씩 복구하도록 했다. 운영 속도는 느리지만 이미 완료된 작업의 중복 실행과 최종 상태 훼손을 방지한다.

#### 초기 고정 임계값과 최소 표본

운영 데이터가 없는 단계에서 Alert가 전혀 없는 상태를 피하기 위해 초기 임계값을 정하고 비율 경보에 최소 처리 건수를 추가했다. 값은 확정된 SLA가 아니라 운영 데이터로 보정할 출발점이다.

#### Circuit Breaker 보류

Kafka는 Outbox, S3는 SDK timeout·재시도, DB는 Pool·쿼리 timeout을 우선 사용했다. 초기 규모에서 Circuit Breaker까지 추가하면 상태와 튜닝 요소가 늘어나므로 Callback 장애가 실제로 Worker 자원 고갈을 일으키는지 관측한 뒤 도입하기로 했다.

### 검토한 대안

#### 고유 식별자를 Prometheus Tag로 사용

Dashboard에서 특정 Job을 바로 찾기 쉽지만 요청마다 새 시계열이 생겨 메모리와 저장 비용이 급증한다. 고유 식별자는 로그에만 남겼다.

#### Gauge 조회 시마다 DB 접근

구현은 단순하고 값이 최신이지만 scrape 주기와 Prometheus 인스턴스 수만큼 DB 조회가 증가한다. 30초 스냅샷의 지연을 감수하고 DB 부하를 제한했다.

#### DLT와 DEAD 자동 재발행

복구 시간은 짧지만 원인이 제거되지 않은 메시지가 반복되고 이미 `DONE` 또는 `FAILED`인 작업을 다시 실행할 수 있다. 상태 확인과 감사 기록을 포함한 단건 수동 절차를 선택했다.

#### 처음부터 실제 Kafka·MySQL·S3 장애만으로 테스트

실제 네트워크와 프로세스 장애를 가장 가깝게 재현하지만 개발·CI 환경 의존성, 긴 실행 시간과 간헐적 실패 비용이 크다. 빠른 영속 상태 통합 테스트를 기본 회귀 방어로 두고 실제 인프라 훈련을 별도로 수행하도록 분리했다.

#### 상용 APM과 관리형 모니터링 우선 도입

분산 추적과 운영 편의 기능을 빠르게 얻을 수 있지만 비용과 제품 종속성이 생긴다. 현재 프로젝트에서는 Actuator, Prometheus, Grafana와 구조화 로그로 기본 원리를 직접 구현하고 필요한 시점에 OpenTelemetry나 관리형 서비스를 검토하기로 했다.

### 감수한 비용

- API와 Worker 양쪽에 로그, 메트릭, 설정과 테스트를 유지해야 한다.
- 30초 Gauge 스냅샷 때문에 장애 상태가 즉시 반영되지 않을 수 있다.
- `correlationId` 전파 규약을 Kafka 메시지와 Callback 양쪽이 계속 지켜야 한다.
- 초기 Alert 임계값은 실제 트래픽과 처리 시간 분포에 따라 오탐·미탐이 발생할 수 있다.
- 수동 단건 재처리는 대량 장애 복구에 시간이 걸린다.
- H2 통합 테스트는 MySQL 잠금 차이, 실제 Kafka 재전달과 S3 네트워크 장애까지 증명하지 않는다.
- Prometheus·Grafana와 Runbook도 코드와 메트릭이 변경될 때 함께 갱신해야 한다.

## 검증

2026-08-31에 API와 Worker에서 단위 테스트와 `integrationTest`를 `--rerun-tasks`로 전체 재실행했다.

| 저장소 | 단위 테스트 | 장애 주입 통합 테스트 | 실패 | 오류 | 건너뜀 |
|---|---:|---:|---:|---:|---:|
| API | 285 | 5 | 0 | 0 | 0 |
| Worker | 33 | 4 | 0 | 0 | 0 |
| 합계 | 318 | 9 | 0 | 0 | 0 |

세부 검증은 다음과 같다.

- Actuator가 `health`, `info`, `prometheus`만 노출하고 Health 상세 정보는 숨기는지 확인
- Prometheus endpoint에 JVM, Job, Outbox, histogram과 공통 `application`, `environment` Tag가 노출되는지 확인
- correlationId 생성·검증·응답 반환, Kafka payload와 Worker Callback 전파 및 MDC 해제 확인
- API·Worker Counter, Timer, Gauge와 30초 DB 스냅샷 갱신 확인
- Prometheus의 API·Worker scrape target, Alert rule, Grafana datasource·Dashboard provisioning 경로 확인
- Alert와 Dashboard 쿼리에 `jobId`, `movieId`, `userId`, `requestId`, `correlationId`가 사용되지 않는지 확인
- Outbox lease 복구, 8회 실패 `DEAD`, Job·Outbox 원자적 롤백 확인
- Kafka 중복 메시지, payload 충돌, Worker lease 복구, Callback-only와 최종 상태 보존 확인
- 중복·역순 Callback 이후 API Job과 Movie 결과 불변 조건 확인
- Runbook의 Alert 12개, Dashboard 패널 15개와 구조화 로그 `eventType` 10개가 실제 설정·코드에 존재하는지 대조
- `git diff --check` 통과

관련 커밋 범위는 API 47개 파일에서 4,170줄 추가·65줄 삭제, Worker 28개 파일에서 1,297줄 추가·69줄 삭제로 확인했다. 이 수치는 성능 향상이 아니라 정책, 코드, 테스트, 모니터링 구성과 운영 문서가 적용된 범위를 나타낸다.

실제 Prometheus·Grafana 컨테이너에서의 장기 수집, Alertmanager 알림 전달과 운영 트래픽 기반 임계값은 이번 자동 검증 범위에 포함하지 않았다. 따라서 MTTR, 장애율이나 처리량 개선 수치를 만들지 않았다.

## 결과

| 변경 전 | 변경 후 |
|---|---|
| 실패 처리 기준이 코드와 설정에 분산 | 재시도 가능 여부, timeout, 상태 전이와 수동 복구 정책을 한 문서로 통일 |
| API·Worker 로그를 개별적으로 검색 | correlationId로 HTTP, Outbox, Kafka, Worker와 Callback 흐름 연결 |
| 장애 징후를 로그에서 수동 발견 | Actuator·Prometheus 메트릭, Grafana Dashboard와 12개 초기 Alert 구성 |
| 고유 식별자의 메트릭 사용 기준 불명확 | 저카디널리티 Tag는 메트릭, 고유 식별자는 로그로 역할 분리 |
| DEAD와 DLT 재처리 기준 부재 | 상태 확인, 단건 조건부 복구와 감사 기록 절차 마련 |
| 정상 경로 중심 테스트 | 프로세스 종료 지점, lease, 반복 실패, 중복·역순 요청의 영속 상태 통합 검증 |
| 장애 발생 시 확인 순서가 개인 지식에 의존 | Alert → Dashboard → 로그 → DB 상태 → 복구 → 정상화 Runbook 구축 |

이번 작업으로 미디어 파이프라인은 실패를 단순히 재시도하는 수준에서 벗어나 실패를 분류하고, 집계 메트릭으로 탐지하고, correlationId로 원인을 추적하고, 상태를 훼손하지 않으면서 복구하는 운영 계약을 갖게 되었다.

실제 운영 장애 감소나 복구 시간 단축 수치는 아직 측정하지 않았다. 대신 구현된 메트릭, Alert, 장애 주입 테스트와 Runbook을 이후 운영 경험을 축적할 기준선으로 마련했다.

## 후속 과제

- Alertmanager에 Slack·이메일 등 실제 수신 채널을 연결하고 비밀값을 배포 환경에서 관리한다.
- 실제 Kafka 네트워크 단절, S3 timeout, Worker 강제 종료와 DB 장애를 운영과 분리된 환경에서 주입하고 Runbook을 보정한다.
- Kafka `max.poll.interval`을 4시간으로 조정해 `2h < 3h < 4h < 4h30` 시간 제한 관계를 완성한다.
- 일반 외부 호출, Callback-only와 전체 인코딩의 Retry Topic 정책을 단계별로 분리한다.
- DB Pool, S3 장애, stale recovery 증가와 Callback 충돌의 전용 Dashboard 패널·Alert 필요성을 운영 데이터로 판단한다.
- 장시간 작업의 Inbox lease heartbeat와 다중 Worker 공유 Inbox 운영 조건을 검토한다.
- 같은 `jobId` 요청 비교가 메시지 포맷 변경에 안전하도록 정규화한 payload hash 도입을 검토한다.
- 실제 처리량, p95·p99 지연, 실패율과 정상 범위를 수집해 Alert 임계값을 조정한다.
- Callback 장애가 Worker 자원 고갈로 이어지는 것이 확인되면 전용 Circuit Breaker를 도입한다.
- 반복되는 수동 단건 복구가 많아지면 승인·감사 기록을 포함한 운영 도구를 구현한다.

## 포트폴리오 요약 후보

Transactional Outbox와 Worker Inbox로 구성된 영상 인코딩 파이프라인을 정상 처리 중심에서 장애 대응 가능한 구조로 확장했습니다. API 요청부터 Kafka·Worker·Callback까지 correlationId를 전파하고 저카디널리티 메트릭, Prometheus·Grafana와 12개 Alert를 구성했으며, 프로세스 종료와 중복·역순 요청을 재현한 9개 장애 주입 통합 테스트 및 상태 기반 Runbook으로 탐지·추적·안전한 복구 절차를 검증했습니다.
