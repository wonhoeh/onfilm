# 미디어 처리 장애 대응 정책

- 작성일: 2026-08-29
- 상태: 목표 정책 확정, 항목별 구현 예정
- 적용 범위: Onfilm API 서버, Kafka, Encoding Worker, S3 호환 스토리지, Worker Callback
- 관련 문서: [미디어 인코딩 Job·Outbox 정책](media-encode-job-outbox-policy.md)

## 1. 목적

영상 인코딩은 API 서버, DB, Kafka, Worker, 스토리지처럼 여러 시스템을 거친다. 어느 한 구간에서 장애가 발생해도 작업을 유실하거나 같은 영상을 여러 번 확정하지 않도록 장애 처리 기준을 통일한다.

이 문서는 다음 질문에 대한 공통 답을 제공한다.

- 같은 Kafka 메시지가 두 번 전달되면 어떻게 처리하는가?
- Worker가 인코딩 도중 종료되면 어디서부터 복구하는가?
- 완료 Callback이 중복되거나 순서가 뒤바뀌면 어떻게 처리하는가?
- 외부 시스템이 느리거나 응답하지 않을 때 몇 번, 어떤 간격으로 재시도하는가?
- DB 커밋 직후 Kafka 발행 전에 서버가 종료되면 요청을 어떻게 복구하는가?
- 자동 복구가 끝내 실패했을 때 무엇을 기록하고 누가 어떻게 재처리하는가?

## 2. 핵심 원칙

1. Kafka 전달 방식은 `at-least-once`로 보고 중복 전달을 정상 상황으로 취급한다.
2. 동일한 `jobId`의 동일한 요청은 여러 번 도착해도 한 번 처리한 것과 같은 결과가 나와야 한다.
3. `DONE`과 `FAILED`는 최종 상태이며, 반대 상태로 덮어쓰지 않는다.
4. 재시도는 일시적 장애에만 제한된 횟수로 수행한다. 잘못된 요청은 즉시 실패 처리한다.
5. 재시도 대기 시간에는 DB 커넥션, 트랜잭션, HTTP 요청 스레드를 점유하지 않는다.
6. 외부 I/O는 긴 DB 트랜잭션 밖에서 실행하고 각 호출에 타임아웃을 둔다.
7. 자동 재시도가 소진된 요청은 삭제하지 않고 Outbox `DEAD` 또는 Kafka DLT에 격리한다.
8. 장애 원인을 추적할 식별자는 로그에 남기되 토큰, 토큰 해시, 비밀 키, Presigned URL은 남기지 않는다.

### Outbox와 Inbox의 역할

- **Outbox**: API 서버가 외부로 보내야 할 메시지를 DB에 먼저 기록하는 발신함이다. 작업 정보와 Outbox를 하나의 트랜잭션으로 저장하여 DB만 반영되고 메시지는 사라지는 문제를 막는다.
- **Inbox**: Worker가 받은 메시지와 처리 단계를 `jobId` 기준으로 기록하는 수신함이다. 같은 메시지가 다시 와도 완료 여부와 중간 지점을 확인하여 중복 실행을 막는다.

## 3. 장애 유형별 구체 정책

| 장애 상황 | 판정 기준 | 자동 처리 | 재시도 종료 후 | 관측 항목 |
|---|---|---|---|---|
| 동일 Kafka 메시지 중복 | `jobId`와 요청 내용이 동일 | Inbox 상태를 확인한다. 처리 중이면 잠시 후 재확인하고, 완료 상태이면 성공으로 종료한다. 출력 업로드까지 끝났다면 Callback만 재시도한다. | 별도 실패로 만들지 않는다. | `duplicate_total{result=busy\|terminal\|callback_only}` |
| 같은 `jobId`에 다른 요청 내용 | 저장된 요청 해시와 새 요청 해시 불일치 | 처리하지 않고 계약 위반으로 분류한다. | DLT 격리, 경고 로그와 수동 조사 | `payload_conflict_total`, `jobId`, `correlationId` |
| Worker가 처리 중 종료 | Kafka offset 미커밋 또는 Inbox lease 만료 | Kafka 재전달과 만료 작업 복구 스케줄러가 다시 점유한다. Inbox 체크포인트부터 재개한다. | 반복 실패 시 재시도 정책에 따라 DLT와 `FAILED` 처리 | lease 만료 수, stale recovery 수, consumer lag |
| 출력 업로드 후 Worker 종료 | Inbox가 `OUTPUT_UPLOADED` | 인코딩과 업로드를 반복하지 않고 완료 Callback만 실행한다. | `FAILURE_PENDING`에 남기고 경고한다. | callback retry 수, pending 체류 시간 |
| 같은 Callback 중복 | 현재 상태와 요청 상태가 동일 | 성공 응답을 반환하는 멱등 no-op으로 처리한다. | 해당 없음 | callback duplicate 수 |
| `DONE` 뒤 `FAILED` 또는 반대 순서 | 현재 상태가 반대 최종 상태 | 상태 변경을 거부하고 HTTP 409를 반환한다. | 원래 최종 상태 유지, 경고 로그 | callback conflict 수, 이전·요청 상태 |
| S3 일시 오류·타임아웃 | 타임아웃, 연결 오류, 5xx, throttling | 호출 타임아웃 후 해당 단계만 제한적으로 재시도한다. | DLT 또는 `FAILED`; 이미 업로드됐다면 Callback-only 상태 유지 | 단계, 시도 횟수, 지연 시간, 오류 코드 |
| 존재하지 않는 원본·잘못된 storage key | 검증 실패, 명확한 4xx, 객체 없음 | 재시도하지 않는다. | 실패 Callback 후 `FAILED` 또는 DLT | `retryable=false`, 검증 오류 코드 |
| API Callback 일시 오류 | 연결 오류, 타임아웃, 5xx, 429 | Callback-only 일정으로 재시도한다. 영상 인코딩은 반복하지 않는다. | `FAILURE_PENDING` 유지, 경고 및 수동 복구 | Callback 실패율, pending 체류 시간 |
| API Callback 영구 오류 | 인증 실패, 잘못된 요청, 상태 충돌 등 재시도로 해결되지 않는 4xx | 재시도하지 않는다. 단, 409 상태 충돌은 별도 운영 경고로 분류한다. | DLT 또는 수동 조사 대상 | HTTP 상태, `errorCode`, 요청 상태 |
| FFmpeg 타임아웃 | 실행 시간이 인코딩 제한 초과 | 1분 후 전체 인코딩을 한 번만 다시 수행한다. | 두 번째 실패 시 DLT 및 `FAILED` | 인코딩 시간, preset, timeout 수 |
| 잘못된 미디어·지원하지 않는 형식 | probe 또는 FFmpeg의 영구 오류 | 재시도하지 않는다. | 실패 Callback과 DLT | 미디어 검증 오류 코드 |
| DB 커밋 후 Kafka 발행 전 API 종료 | Job과 Outbox는 커밋됐지만 Outbox가 `PUBLISHED`가 아님 | Publisher가 커밋된 Outbox를 조회하여 발행한다. | 8회 실패하면 Outbox `DEAD` | pending 수, oldest age, dead 수 |
| Kafka 발행 성공 후 상태 저장 전 API 종료 | Kafka에는 전달됐지만 Outbox가 미완료 | lease 만료 후 Outbox를 다시 발행한다. Worker Inbox가 중복을 제거한다. | 반복 발행 실패는 Outbox `DEAD` | 재발행 수, Worker duplicate 수 |
| Job·Outbox 저장 중 DB 오류 | 같은 DB 트랜잭션이 롤백 | Job과 Outbox 모두 저장하지 않고 요청 실패를 반환한다. | 클라이언트가 새 요청으로 다시 시도 | DB 오류율, 롤백 수 |
| Outbox `DEAD` 또는 Kafka DLT 발생 | 자동 재시도 소진 | 자동 삭제·무조건 재발행하지 않는다. 원인 수정과 상태 확인 후 운영자가 재처리한다. | 보존 기간 이후 감사 절차에 따라 정리 | dead/DLT 건수와 oldest age |

## 4. 재시도 정책

### 4.1 재시도 가능 여부

다음 오류만 재시도한다.

- 연결 실패와 응답 타임아웃
- HTTP 429 및 5xx
- Kafka 일시 발행 실패
- S3 throttling과 일시적인 5xx
- Worker 프로세스 종료와 lease 만료
- 일시적인 DB 연결 실패

다음 오류는 재시도하지 않는다.

- 필수값, storage key, preset 등 요청 검증 실패
- 존재하지 않는 원본 파일이 최종 확인된 경우
- 지원하지 않는 미디어 형식
- 인증·서명 검증 실패
- 동일 `jobId`의 요청 내용 불일치
- 허용되지 않는 최종 상태 전이

### 4.2 작업 종류별 일정

재시도 횟수는 최초 시도를 제외한 추가 시도 횟수다.

| 구분 | 재시도 간격 | 총 시도 수 | 마지막 시도 시점 | 실행 방식 |
|---|---|---:|---:|---|
| 일반 외부 호출 | 10초 → 30초 → 2분 → 10분 | 최초 1회 + 재시도 4회 | 최초 시점부터 12분 40초 후 | 다음 실행 시각을 저장하고 스케줄러 또는 Retry Topic으로 실행 |
| Callback-only | 10초 → 30초 → 2분 → 10분 → 30분 | 최초 1회 + 재시도 5회 | 최초 시점부터 42분 40초 후 | 출력물을 유지하고 Callback만 실행 |
| 전체 인코딩 재실행 | 1분 | 최초 1회 + 재시도 1회 | 최초 실패 1분 후 | 새 Worker 처리 시도로 실행 |
| 영구 오류 | 없음 | 1회 | 즉시 | 실패 보고 후 DLT 또는 `FAILED` |

예를 들어 일반 외부 호출은 하나의 메서드가 12분 40초 동안 잠드는 방식이 아니다. 실패 시 트랜잭션을 끝내고 `nextAttemptAt`을 저장한 뒤 반환한다. 스케줄러가 실행 시각이 된 건을 새 짧은 트랜잭션으로 점유하므로 대기 중 DB 커넥션과 애플리케이션 스레드를 사용하지 않는다.

### 4.3 Outbox 발행 정책

현재 구현된 Outbox 정책을 유지한다.

- 총 발행 시도: 최대 8회
- 실패 후 간격: 2초 → 4초 → 8초 → 16초 → 32초 → 64초 → 128초
- 점유 lease: 2분
- 한 번에 점유할 최대 건수: 50건
- Kafka 발행 대기 한도: 건당 30초
- 8번째 실패: `DEAD`
- `DEAD` 레코드: 자동 삭제하거나 자동 재발행하지 않음

Outbox의 짧은 재시도 정책은 사용자 Callback보다 빠르게 메시지 전달을 복구하기 위한 별도 정책이다. 장애가 장기화되면 빠르게 `DEAD`로 격리하여 무한 발행과 장애 증폭을 막는다.

### 4.4 Kafka Worker 정책의 목표값

- 소비 의미: at-least-once
- 레코드 처리 성공 후에만 offset 커밋
- 한 번에 가져올 레코드: 1건
- Consumer 동시성: 초기 1, 수평 확장 시 공유 Inbox를 전제로 확대
- 일반 처리: 최초 1회와 최대 4회 재시도
- 전체 인코딩: 비용이 크므로 최초 실패 후 1분 뒤 한 번만 재실행
- 재시도 소진: DLT 전송

현재 Worker의 공통 Retry Topic은 1초, 2초, 4초, 8초 간격의 총 5회 시도를 사용한다. 단계별 비용과 복구 지점을 반영하도록 일반 외부 호출, Callback-only, 전체 인코딩 정책으로 분리하는 것은 후속 구현 대상이다.

## 5. 상태 전이와 Callback 정책

처리 시작 Callback이 유실되더라도 완료 결과를 반영할 수 있도록 `REQUESTED → DONE`을 허용한다.

| 현재 상태 | 요청 상태 | 처리 결과 |
|---|---|---|
| `REQUESTED` | `PROCESSING` | 허용 |
| `REQUESTED` | `DONE` | 허용 |
| `REQUESTED` | `FAILED` | 허용 |
| `PROCESSING` | `PROCESSING` | 2xx, 변경 없는 성공 |
| `PROCESSING` | `DONE` | 허용 |
| `PROCESSING` | `FAILED` | 허용 |
| `DONE` | `DONE` | 2xx, 변경 없는 성공 |
| `DONE` | `FAILED` 또는 `PROCESSING` | 409, 기존 `DONE` 유지 |
| `FAILED` | `FAILED` | 2xx, 변경 없는 성공 |
| `FAILED` | `DONE` 또는 `PROCESSING` | 409, 기존 `FAILED` 유지 |

Callback 처리 규칙은 다음과 같다.

- Callback 인증에는 timestamp, nonce, 요청 본문 해시를 포함한 HMAC-SHA256 서명을 사용한다.
- 허용 시간 범위를 벗어난 timestamp와 재사용된 nonce는 거부한다.
- 인증 정보, 원문 서명, 비밀 키는 로그에 남기지 않는다.
- 완료 Callback 전에 출력 객체 존재 여부를 확인한다.
- 객체 확인 같은 외부 I/O는 DB 트랜잭션 밖에서 실행한다.
- Movie 결과 반영과 Job 상태 변경은 하나의 DB 트랜잭션으로 처리한다.

## 6. 트랜잭션과 lease 정책

외부 호출을 포함한 처리 흐름은 다음처럼 분리한다.

```text
짧은 트랜잭션: 처리 대상 점유 및 lease 기록
        ↓ commit
트랜잭션 없음: Kafka·S3·Callback·FFmpeg 실행
        ↓
짧은 트랜잭션: 성공 상태 또는 다음 재시도 시각 기록
```

- 요청 접수 시 `MediaEncodeJob`과 `MediaEncodeOutbox`는 하나의 트랜잭션으로 저장한다.
- Outbox 점유, 성공 처리, 실패 및 다음 시도 기록은 각각 독립된 짧은 트랜잭션으로 처리한다.
- 네트워크 응답을 기다리는 동안 DB 트랜잭션을 열어 두지 않는다.
- 프로세스 종료 후에도 복구할 수 있도록 재시도 횟수와 다음 시각은 메모리가 아니라 DB에 저장한다.
- lease는 작업 최대 시간보다 길어야 하며, 복구 지연을 줄이기 위해 필요하면 heartbeat로 연장한다.

### 시간 제한의 목표 관계

```text
FFmpeg timeout 2시간
    < Inbox lease 3시간
    < Kafka max.poll.interval 4시간
    < API Job timeout 4시간 30분
```

이 순서를 지켜 정상 인코딩이 진행 중인데 lease가 먼저 만료되거나 API가 먼저 작업을 실패 처리하는 일을 막는다. API Job timeout은 4시간 30분으로 조정했다. Worker의 Inbox lease와 Kafka `max.poll.interval`은 각각 3시간으로 동일하므로 Kafka `max.poll.interval`을 4시간으로 조정하는 작업은 별도로 필요하다.

## 7. 외부 시스템 타임아웃과 Circuit Breaker

| 대상 | 연결·호출 제한 | 정책 |
|---|---|---|
| Core API Callback | 연결 5초, 읽기 30초 | 제한된 Callback-only 재시도. 4xx는 원칙적으로 재시도 제외 |
| S3 메타데이터 확인 | 호출당 5초, 전체 10초 목표 | 짧게 실패를 감지하고 일반 외부 호출 정책 적용 |
| S3 대용량 전송 | 시도당 3분, 전체 10분 | SDK 표준 재시도 사용, 전체 제한을 넘기면 Worker 단계 재시도 |
| Kafka 발행 | 건당 30초 | Outbox에 실패를 기록하고 다음 시각에 재발행 |
| FFmpeg | 2시간 | 1분 뒤 한 번만 전체 재실행 |
| 일반 DB 작업 | 커넥션 획득 3초, 쿼리 5~10초 목표 | Circuit Breaker 대신 풀·쿼리·트랜잭션 타임아웃 사용 |

Circuit Breaker는 초기 필수 구성으로 두지 않는다.

- Kafka는 Outbox와 발행 타임아웃으로 장애를 격리한다.
- S3는 AWS SDK의 타임아웃과 표준 재시도를 먼저 사용한다.
- DB에는 Circuit Breaker를 적용하지 않고 커넥션 풀과 쿼리 타임아웃을 사용한다.
- Callback 장애가 연쇄적으로 Worker 자원을 고갈시키는 것이 관측되면 Callback 전용 Circuit Breaker를 추가한다. 후보 기준은 최소 10건을 포함한 최근 20건 중 실패율 50%, 30초 Open, Half-open 시험 3건이며 4xx는 실패율에서 제외한다.

## 8. DLT와 수동 재처리

Outbox `DEAD`와 Kafka DLT는 서로 다른 실패 지점이다.

- Outbox `DEAD`: API에서 Kafka로 메시지를 발행하지 못한 요청
- Kafka DLT: Worker가 메시지는 받았지만 자동 처리를 완료하지 못한 요청

운영 정책은 다음과 같다.

- 보존 기간: 최소 14일
- 자동 재투입 금지
- 원인 수정, 원본 파일 존재, 현재 Job 상태, 요청 내용 일치 여부를 확인한 뒤 재처리
- 재처리할 때 기존 `jobId`를 유지하여 Inbox 멱등성을 이용
- 작업자, 실행 시각, 사유, 원본 topic·partition·offset을 감사 로그로 기록
- 이미 `DONE` 또는 `FAILED`인 Job은 상태 전이 정책을 먼저 확인하고 무조건 재처리하지 않음

구체적인 상태 확인, 조건부 DB 변경, DLT 단건 재발행과 감사 항목은 [미디어 Outbox DEAD·Kafka DLT 재처리 절차](../operations/media-dead-letter-reprocessing.md)를 따른다. 현재 상태 정책상 API Job이 이미 `FAILED`라면 같은 `jobId`를 재실행하지 않고 새 업로드 요청으로 새 Job을 생성한다.

## 9. 로그와 메트릭

### 로그 필드

장애 관련 구조화 로그에는 가능한 범위에서 다음 값을 사용한다.

- `service`, `environment`
- `correlationId`, `requestId`
- `userId`, `jobId`, `movieId`
- `eventType`, `stage`
- `fromStatus`, `toStatus`
- `attempt`, `retryable`
- `elapsedMs`, `errorCode`
- Kafka `topic`, `partition`, `offset`

로그에 남기지 않는 값:

- 액세스·리프레시 토큰과 토큰 해시
- Callback HMAC 원문 서명과 비밀 키
- Presigned URL과 인증 헤더
- 사용자가 올린 요청·응답 본문 전체

### correlationId 전파 규칙

- 외부 요청은 `X-Correlation-Id` 헤더를 받을 수 있다. 영문 대소문자, 숫자, `.`, `_`, `-`로 이루어진 1~64자 값만 사용한다.
- 헤더가 없거나 허용 형식이 아니면 API가 UUID를 새로 생성한다. 확정된 값은 응답의 `X-Correlation-Id` 헤더에도 반환한다.
- `correlationId`는 한 번의 분산 처리 흐름을 추적하는 관측 식별자다. 업로드 완료 요청의 멱등성을 식별하는 `requestId`와 역할을 섞지 않는다.
- API는 Job과 함께 저장하는 Outbox payload에 `correlationId`를 기록한다. Publisher는 payload에서 값을 복원하여 Kafka 발행 로그에 사용한다.
- Worker는 Kafka 메시지의 `correlationId`를 소비, 인코딩, stale recovery, 실패 보고 로그에 사용하고 모든 내부 Callback의 `X-Correlation-Id` 헤더로 전달한다.
- 기존 schema version 1 메시지처럼 `correlationId`가 없는 메시지는 `requestId`를 대체값으로 사용한다. 선택 필드 추가이므로 schema version은 올리지 않는다.
- MDC 값은 요청이나 작업 단위 범위를 벗어나지 않도록 자동 해제한다. 스레드 풀에서 이전 작업의 식별자가 다음 작업에 섞이지 않아야 한다.
- 운영 `prod` 프로필은 검색 가능한 JSON 로그를 출력하고, 로컬·테스트 환경은 같은 핵심 필드를 `key=value` 형식으로 출력한다.
- `jobId`, `movieId`, `userId`, `requestId`, `correlationId`는 로그 검색에만 사용하며 메트릭 태그로 사용하지 않는다.

### 필수 메트릭

- Outbox pending·dead 개수와 가장 오래된 pending의 나이
- Outbox 발행 성공·실패·재시도 횟수
- Kafka Consumer lag와 DLT 유입 수
- Inbox 중복, payload 충돌, lease 만료 복구 횟수
- Callback 성공·실패·중복·상태 충돌·재시도 횟수
- 인코딩 성공률, 실패율, 평균·상위 구간 처리 시간, timeout 수
- `FAILURE_PENDING` 개수와 가장 오래된 항목의 나이
- HTTP 요청 수, 5xx 비율과 응답 시간
- DB 커넥션 풀 사용량과 대기 시간

`jobId`, `movieId`, `requestId` 같은 고유 식별자는 메트릭 태그로 사용하지 않는다. 메트릭 시계열 폭증을 막고, 개별 추적은 로그에서 수행한다.

### 미디어 처리 메트릭 명세

애플리케이션에서 사용하는 Micrometer 이름과 저카디널리티 태그는 다음과 같다. Prometheus에 노출될 때 이름은 `_` 구분자로 변환되고 Counter에는 `_total` 접미사가 붙을 수 있다.

| Micrometer 이름 | 종류 | 태그 | 의미 |
|---|---|---|---|
| `media.encode.outbox.publish` | Counter | `result=success\|failure` | Kafka 발행 성공·실패 횟수 |
| `media.encode.outbox.failure` | Counter | `result=retry_scheduled\|dead` | 발행 실패 후 재시도 예약 또는 DEAD 전환 횟수 |
| `media.encode.outbox.records` | Gauge | `status=pending\|publishing\|published\|dead` | 상태별 Outbox 현재 건수 |
| `media.encode.outbox.oldest.pending.age` | Gauge | 없음 | 가장 오래된 PENDING Outbox의 체류 시간(초) |
| `media.encode.callback` | Counter | `type=processing\|complete\|fail`, `result=applied\|duplicate\|conflict\|error` | Callback 처리 결과 횟수 |
| `media.encode.callback.duration` | Timer | `type`, `result` | Callback 처리 시간 |
| `media.encode.job.created` | Counter | `type=movie\|trailer\|thumbnail` | 새로 생성된 Job 횟수. 멱등 요청으로 기존 Job을 반환한 경우 제외 |
| `media.encode.job.transition` | Counter | `type`, `status=processing\|done\|failed` | 실제로 적용된 Job 상태 전이 횟수 |
| `media.encode.job.terminal` | Counter | `type`, `result=success\|failure\|timeout` | 최종 상태 도달 횟수 |
| `media.encode.job.duration` | Timer | `type`, `result` | 요청 시각부터 최종 완료 시각까지 걸린 시간 |
| `media.encode.job.timeout` | Counter | `type` | 유지보수 스케줄러가 timeout 처리한 Job 횟수 |
| `media.encode.job.records` | Gauge | `status=requested\|processing\|done\|failed` | 상태별 Job 현재 건수 |

Worker는 인코딩 시도와 각 단계의 병목·실패 지점을 구분하기 위해 다음 메트릭을 사용한다.

| Micrometer 이름 | 종류 | 태그 | 의미 |
|---|---|---|---|
| `media.encode.worker.attempt` | Counter | `type`, `result=success\|failure` | Worker 인코딩 시도 결과 횟수 |
| `media.encode.worker.attempt.duration` | Timer | `type`, `result` | 한 Worker 처리 시도의 전체 시간 |
| `media.encode.worker.stage.duration` | Timer | `type`, `stage=validation\|download\|probe\|transcode\|upload\|callback`, `result=success\|failure` | 단계별 처리 시간과 실패 구간 |
| `media.encode.worker.failure` | Counter | `type`, `stage`, `code`, `retryable` | 단계·내부 오류 코드·재시도 가능 여부별 실패 횟수 |
| `media.encode.worker.inbox.claim` | Counter | `result=process\|callback_only\|busy\|terminal` | Inbox 점유 결과와 중복 처리 방지 결과 |
| `media.encode.worker.inbox.records` | Gauge | `status` | Inbox 상태별 현재 건수 |
| `media.encode.worker.inbox.oldest.failure.pending.age` | Gauge | 없음 | 가장 오래된 `FAILURE_PENDING` 체류 시간(초) |
| `media.encode.worker.callback` | Counter | `type=complete\|failure`, `result=success\|retry\|permanent_failure\|error` | 완료·실패 Callback 결과 횟수 |
| `media.encode.worker.callback.duration` | Timer | `type`, `result` | Worker Callback 처리 시간 |
| `media.encode.worker.stale.recovery` | Counter | `result=started\|success\|failure` | lease 만료 작업 복구 시도와 결과 |
| `media.encode.worker.dlt` | Counter | `result=received\|invalid` | DLT 유입 및 역직렬화 후 식별 불가능한 메시지 횟수 |

- 현재 건수를 나타내는 Gauge는 Prometheus scrape마다 DB를 조회하지 않는다. 기본 30초 간격으로 DB 상태를 읽어 메모리 스냅샷을 갱신한다.
- Counter와 Timer는 프로세스 재시작 시 초기화되므로 Prometheus의 시간 범위 함수로 증가량과 비율을 계산한다.
- Callback의 `duplicate`는 동일한 최종 결과를 다시 받은 멱등 성공이고, `conflict`는 반대 최종 상태처럼 허용되지 않는 상태 전이다.
- `failureCode`는 외부 Worker가 전달하는 문자열이므로 메트릭 태그로 사용하지 않는다. 구체적인 오류 분석은 `correlationId`, `jobId`, `errorCode`가 포함된 구조화 로그에서 수행한다.
- Worker의 `media.encode.worker.failure`에 사용하는 `code`는 코드에 정의된 `FailureCode` enum으로 값의 범위가 제한되어 있어 태그로 허용한다. 외부 문자열을 그대로 태그로 변환하지 않는다.
- `jobId`, `movieId`, `userId`, `requestId`, `correlationId`, storage key는 어떤 메트릭에도 태그로 추가하지 않는다.

### 초기 경보 기준

| 경보 | 초기 기준 | 심각도 |
|---|---|---|
| Outbox dead 발생 | 1건 이상 | Critical |
| Outbox 적체 | 가장 오래된 pending이 2분 초과 | Warning |
| Kafka DLT 유입 | 1건 이상 | Warning, 증가 지속 시 Critical |
| Consumer 지연 | oldest lag 15분 초과 | Warning |
| 인코딩 실패 | 15분 동안 최소 10건 중 10% 초과 또는 3건 연속 실패 | Warning |
| 인코딩 timeout | 1건 이상 | Warning |
| Callback 미보고 | `FAILURE_PENDING` 10분 초과 | Warning |
| Worker 비정상 | health check 연속 실패 | Critical |
| API 5xx | 5분 동안 요청이 충분한 구간에서 5% 초과 | Critical |

초기 기준은 운영 데이터를 수집한 뒤 정상 범위와 트래픽 규모에 맞게 조정한다.

Prometheus rule 구현에서는 현재 수집 가능한 메트릭의 한계를 다음처럼 반영한다.

- Kafka 레코드의 생성 시각을 제공하는 메트릭이 없으므로 Consumer 지연은 `record lag > 0`이 15분 동안 지속되는 조건으로 판정한다.
- Counter만으로 실패의 정확한 연속 순서를 복원할 수 없으므로 3건 연속 실패 대신 `5분 내 실패 3건`을 조기 경보로 사용한다.
- 비율 경보는 소량 트래픽의 우연한 1건 실패를 과대평가하지 않도록 인코딩 10건, HTTP 20건의 최소 표본 조건을 둔다.
- 경보 labels에는 `severity`, `service`, `category`만 사용하고 고유 식별자는 구조화 로그 조회에 사용한다.

### Prometheus·Grafana 수집 구성

로컬 관측 환경은 `infra/monitoring`의 Docker Compose와 provisioning 파일을 단일 기준으로 사용한다.

- Prometheus는 15초마다 API `:8080/actuator/prometheus`와 Worker `:8082/actuator/prometheus`를 수집한다.
- 개발 환경의 시계열 보존 기간은 기본 15일이며 환경 변수로 변경할 수 있다.
- Grafana는 Prometheus datasource와 `Onfilm Media Operations` Dashboard를 시작 시 자동 등록한다.
- Dashboard는 API Job·Outbox·Callback과 Worker 인코딩·단계별 지연·Inbox·stale recovery·DLT를 한 화면에서 확인한다.
- 처리 시간 p95는 API와 Worker Timer에 활성화한 Prometheus histogram bucket으로 계산한다.
- 공통 태그는 `application`, `environment`로 제한한다. 개별 요청 식별자는 구조화 로그에서 조회한다.
- 로컬 Prometheus와 Grafana 포트는 `127.0.0.1`에만 바인딩한다. 운영 Actuator는 인터넷에 공개하지 않고 사설망과 방화벽으로 수집 주체만 허용한다.

실행·확인·초기화 방법은 [로컬 모니터링 실행 가이드](../../infra/monitoring/README.md)를 따른다. 초기 경보 기준은 Prometheus rule로 구현했으며, 운영 알림 수신 채널과 비밀값은 배포 환경의 Alertmanager에서 연결한다.

## 10. 장애 주입과 검증 기준

다음 시나리오는 자동화 테스트 또는 로컬 장애 주입으로 검증한다.

1. 동일한 `jobId`와 동일한 내용의 Kafka 메시지를 두 번 전달한다.
2. 동일한 `jobId`에 다른 내용의 메시지를 전달한다.
3. Inbox 점유 직후와 FFmpeg 실행 중 Worker를 종료한다.
4. 출력 업로드 직후, 완료 Callback 직전에 Worker를 종료한다.
5. `DONE` Callback을 두 번 보내고 `DONE → FAILED`, `FAILED → DONE` 순서로 보낸다.
6. S3, Kafka, Core API에 연결 실패와 타임아웃을 주입한다.
7. Job과 Outbox 저장 중 DB 오류를 발생시킨다.
8. API DB 커밋 직후 Outbox 발행 전에 프로세스를 종료한다.
9. Kafka 전송 성공 직후 Outbox를 `PUBLISHED`로 바꾸기 전에 Publisher를 종료한다.
10. Retry 소진 후 DLT·`DEAD` 레코드를 확인하고 수동 재처리한다.

검증이 성공하려면 다음 불변 조건을 만족해야 한다.

- Job만 저장되고 Outbox가 없는 상태가 생기지 않는다.
- 메시지 중복이 영화 결과 중복 반영으로 이어지지 않는다.
- 출력 업로드 이후 장애에서는 FFmpeg를 다시 실행하지 않는다.
- 최종 상태가 늦게 도착한 반대 Callback으로 변경되지 않는다.
- 재시도 대기 중 DB 커넥션과 요청 스레드를 점유하지 않는다.
- 자동 재시도 소진 건은 추적 가능한 상태로 남는다.

## 11. 현재 구현과 후속 작업

| 항목 | 현재 상태 | 후속 작업 |
|---|---|---|
| Job과 Outbox 원자 저장 | 구현됨 | 장애 주입 회귀 테스트 유지 |
| Outbox lease·8회 재시도·`DEAD` | 메트릭과 경보까지 구현됨 | 수동 재처리 절차 추가 |
| Worker `jobId` Inbox 멱등 처리 | 구현됨 | 공유 DB 전환 전에는 단일 Worker 운영 제약 명시 |
| 같은 `jobId`의 요청 내용 비교 | 미구현 | 정규화한 payload hash 저장 및 불일치 DLT 처리 |
| Worker Retry Topic과 DLT | 공통 정책으로 구현됨 | 단계별 일반·Callback-only·인코딩 정책으로 분리 |
| 출력 업로드 후 Callback-only 복구 | 구현됨 | 재시도 횟수와 42분 40초 일정을 명시적으로 제한 |
| Callback 상태 전이 검증 | 구현됨 | 충돌 전용 메트릭과 경보 추가 |
| Worker stale recovery | 구현됨 | lease heartbeat와 복구 테스트 보강 |
| 외부 I/O와 DB 트랜잭션 분리 | 주요 흐름에 구현됨 | 신규 흐름의 코드 리뷰 체크리스트에 포함 |
| 시간 제한 관계 | API Job timeout 4시간 30분 반영, Worker 일부 불일치 | Kafka `max.poll.interval`을 4시간으로 조정하여 2h < 3h < 4h < 4h30 관계 완성 |
| 관측성 | API·Worker Prometheus endpoint, correlationId 전파, 구조화 로그, 미디어 메트릭, Prometheus·Grafana와 초기 Alert rule 구현 | 운영 Alertmanager 수신 채널 연결 및 임계값 보정 |
| DLT 운영 | 원본 위치 구조화 로그, 14일 보존 설정과 단건 재처리 절차 문서화 | 승인된 일괄 복구 도구와 통합 Runbook 작성 |

이 문서는 목표 동작의 기준이다. 후속 구현에서 값이 바뀌면 코드만 변경하지 않고 이 문서와 테스트를 함께 갱신한다.
