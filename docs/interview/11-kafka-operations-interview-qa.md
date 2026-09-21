# Kafka·운영 면접 질문과 답변

- 작성일: 2026-09-08
- 구성: 최초 질문 1~13번, 추가 예상 질문 14~34번을 면접 답변·OnFilm 사례·주의사항으로 정리
- 기준: API와 별도 Worker 저장소의 현재 코드, 장애 대응 정책, Runbook, EC2 배포 Workflow
- 주의: AWS 성능 측정 환경에서의 검증 경험을 대규모 실서비스 운영 경험으로 표현하지 않는다. 현재 구현과 후속 정책 목표를 구분한다.

## 1. Kafka Consumer가 같은 메시지를 두 번 처리할 수 있는 이유는?

### 면접 답변

“메시지 처리는 끝났지만 어디까지 처리했는지를 Kafka에 기록하기 전에 Consumer가 종료될 수 있기 때문입니다. 재시작한 Consumer는 마지막으로 기록된 위치부터 읽으므로 같은 메시지를 다시 받습니다. 발행 측에서도 전송 성공을 기록하지 못해 같은 작업을 다시 발행할 수 있어, 중복을 전제로 처리해야 합니다.”

### 두 가지 중복 경로

1. 소비 측: DB에 결과 저장 → offset 커밋 전 종료 → 같은 레코드 재전달
2. 발행 측: Kafka 발행 성공 → Outbox의 `PUBLISHED` 저장 전 종료 → lease 만료 후 재발행

첫 번째는 같은 Topic·Partition·Offset을 다시 읽는 경우이고, 두 번째는 같은 `jobId`의 메시지가 다른 Offset으로 저장될 수 있는 경우다. 그래서 Offset만으로 업무 중복을 판단하지 않는다.

### 프로젝트 사례와 주의사항

- Worker 설정은 `enable-auto-commit: false`, `ack-mode: record`다. offset 처리는 Listener Container가 담당한다.
- 실패 메시지가 Retry Topic이나 DLT로 이동한 경우 원본 offset은 진행될 수 있다. 원본 offset 커밋이 인코딩 성공을 뜻하지는 않는다.
- Consumer 재시작·재할당 자체가 항상 중복을 만드는 것은 아니다. 실제 처리와 커밋 사이의 불일치가 핵심이다.
- Producer의 멱등 전송 설정만으로 애플리케이션이 별도로 재발행한 같은 업무나 Consumer의 DB 중복 반영까지 막지는 못한다.

근거: [Outbox 전달 정책](../decisions/media-encode-job-outbox-policy.md), [Kafka 전달 의미 설명](https://kafka.apache.org/40/design/design/)

## 2. At-least-once는 무엇인가?

### 면접 답변

“메시지가 한 번 이상 전달될 수 있는 방식입니다. 처리 여부가 불확실하면 다시 전달하므로 누락 위험을 줄이는 대신 중복 가능성이 생깁니다. OnFilm은 메시지를 정확히 한 번만 전달하려 하기보다, 여러 번 받아도 결과가 중복 반영되지 않도록 설계했습니다.”

### 구분해서 설명할 것

- At-most-once: 전달·처리 시도가 최대 한 번이므로 중복은 줄지만 누락을 감수할 수 있다.
- At-least-once: 재전달을 허용하므로 소비자가 중복에 대비해야 한다.
- Exactly-once: 어떤 처리 범위에서 결과가 한 번만 반영되는지 명확히 해야 한다. Kafka 내부의 트랜잭션 보장이 외부 DB·S3·HTTP까지 자동으로 확장되지는 않는다.

### 프로젝트의 보장 범위

Outbox는 발행 요청을 DB에 남기고 재시도한다. 하지만 시도 한도를 넘으면 `DEAD`로 격리한다. 따라서 ‘어떤 장애에도 모든 작업이 반드시 성공한다’는 보장은 아니다. 복구 가능성은 DB·Kafka의 데이터 보존, 인프라 내구성, 재처리 절차에도 의존한다.

근거: [Kafka 설계 문서](https://kafka.apache.org/40/design/design/), [프로젝트 장애 대응 정책](../decisions/media-failure-handling-policy.md)

## 3. 중복 메시지를 어떻게 안전하게 처리하는가?

### 면접 답변

“메시지마다 업무를 식별하는 키를 정하고, 서버 DB에 처리 상태를 저장합니다. 같은 키가 다시 오면 처음부터 실행하지 않고 이미 완료했는지, 다른 작업자가 처리 중인지, 어느 단계부터 이어가야 하는지 판단합니다. 동시에 도착할 수도 있으므로 DB 제약과 잠금도 함께 사용합니다.”

### OnFilm의 Inbox 처리

Inbox는 ‘받은 작업과 진행 상황을 보관하는 DB 기록’이다. Worker의 `jobId`를 기본 키로 사용한다.

| 저장 상태 | 다시 메시지를 받았을 때 |
|---|---|
| 기록 없음 | Inbox를 생성하고 처리 시작 |
| `PROCESSING`, 유효한 lease | 다른 Worker가 처리 중인 것으로 보고 중복 실행하지 않음 |
| `PROCESSING`, lease 만료 | 다시 선점해 처리 재개 가능 |
| `OUTPUT_UPLOADED`, lease 만료 | 영상 변환을 생략하고 Callback만 재시도 |
| `DONE` 또는 `FAILED` | 일반 처리 경로에서 건너뜀 |
| `FAILURE_PENDING` | 일반 처리는 건너뛰고 별도 실패 보고 경로에서 처리 |

- 기존 Inbox는 행 잠금으로 조회하며, 같은 `jobId`에 다른 Kafka key나 저장 payload가 들어오면 정상 중복으로 인정하지 않는다.
- 최초 INSERT 경쟁은 DB 기본 키로 막고, 충돌 뒤 별도 트랜잭션에서 기존 기록을 다시 확인한다.
- lease는 일정 시간의 처리 권한이다. 선점 트랜잭션이 끝난 뒤에도 처리 중임을 표시하지만, 외부 FFmpeg 프로세스를 물리적으로 잠그는 것은 아니다.

### 한계

외부 업로드 직후 체크포인트 저장 전에 종료되면 외부 작업이 반복될 수 있다. lease가 만료됐는데 이전 Worker가 계속 실행되는 상황도 별도 고려가 필요하다. 따라서 ‘FFmpeg가 모든 장애에서 정확히 한 번 실행된다’고 주장하지 않는다. 기록 보존 기간보다 늦은 재전달도 검토 대상이다.

코드 확인 위치: Worker 저장소의 `InboxTransactionService`, `InboxClaimCoordinator`, `MediaEncodeInbox`, `EncodingJobProcessor`.

근거 문서: [미디어 처리 개선 사례](../problem-solving/05-media-encode-outbox-and-worker.md), [Runbook](../operations/media-incident-runbook.md)

## 4. Idempotency는 무엇인가?

### 면접 답변

“같은 요청을 여러 번 처리해도 한 번 처리했을 때와 같은 업무 결과를 유지하는 성질입니다. 예를 들어 영상 업로드 완료 요청이 반복돼도 인코딩 작업이 계속 추가되지 않도록 하는 것입니다. 응답 시각이나 로그까지 모두 같다는 뜻은 아닙니다.”

### 프로젝트의 세 가지 적용 지점

| 지점 | 기준 | 유지할 결과 |
|---|---|---|
| 업로드 완료 API | `requestId`와 요청 내용 | 동일 요청에 기존 Job을 반환 |
| Worker 메시지 처리 | `jobId`와 메시지 내용 | 저장한 상태에 따라 중복 실행 제한 |
| 완료 Callback | `jobId`, 현재 상태, 예상 출력 정보 | 같은 완료 결과를 다시 반영하지 않음 |

### 주의사항

- 같은 키에 다른 요청 내용이 들어오면 거절해야 한다. 무조건 기존 성공 응답을 돌려주면 잘못된 요청을 숨길 수 있다.
- 메모리의 `Set`만으로는 서버 재시작이나 여러 인스턴스 간 중복을 통제하기 어렵다.
- 존재 여부를 확인한 뒤 INSERT하는 코드만으로는 동시 요청을 막을 수 없다. DB 제약과 트랜잭션이 필요하다.
- 로그·메트릭은 중복 요청 횟수만큼 늘 수 있다. 업무 결과의 멱등성과 관측 기록의 반복은 구분한다.

근거: [Job·Outbox 식별자 정책](../decisions/media-encode-job-outbox-policy.md), [Job 요청 서비스](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCommandService.java)

## 5. Kafka 메시지 처리와 DB Transaction 사이의 일관성을 어떻게 처리할 것인가?

### 면접 답변

“DB 저장과 Kafka 전송을 하나의 일반 DB 트랜잭션으로 묶을 수는 없습니다. OnFilm은 보내는 쪽에서는 작업 정보와 발행할 메시지를 같은 DB 트랜잭션에 저장하는 Outbox를 사용했습니다. 받는 쪽에서는 Inbox에 진행 상태를 남겨 재전달에 대응하고, 최종 결과와 Job 완료 상태는 API DB의 한 트랜잭션에서 반영했습니다.”

### 경계별 처리

1. API DB: Job과 Outbox를 함께 저장한다. 하나라도 실패하면 둘 다 롤백한다.
2. Publisher: 짧은 트랜잭션으로 선점한 뒤 DB 트랜잭션 밖에서 Kafka를 호출한다. 발행 확인 후 별도 트랜잭션으로 상태를 저장한다.
3. Worker DB: Inbox 선점·체크포인트를 짧게 저장하고 S3·FFmpeg·Callback은 트랜잭션 밖에서 수행한다.
4. API 완료 처리: 출력 존재를 외부에서 확인한 다음, 트랜잭션 안에서 Job 상태와 요청 내용을 다시 검사하고 Movie 결과·Job 완료를 함께 저장한다.

### 대안과 한계

- 단순한 Kafka→DB 소비라면 ‘중복 처리 기록과 업무 변경을 같은 DB 트랜잭션에 저장하고, 커밋 후 offset을 진행’하는 방식도 가능하다. OnFilm은 긴 외부 처리가 있어 단계별 체크포인트를 사용한다.
- DB 트랜잭션과 Kafka 트랜잭션의 커밋 순서를 맞춰도 두 번째 커밋만 실패할 수 있다. 단순히 `@Transactional`을 붙였다고 두 시스템의 원자성이 생기지는 않는다.
- API와 Worker는 서로 다른 논리 DB를 소유한다. 전체 흐름은 한 번의 롤백으로 되돌리는 방식이 아니라, 재시도와 상태 검증으로 맞춰 가는 방식이다.

근거: [Outbox Publisher](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeOutboxPublisher.java), [완료 트랜잭션](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCompletionTransactionService.java), [DB 소유권 정책](../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md), [Spring Kafka 트랜잭션 동기화의 한계](https://docs.spring.io/spring-kafka/reference/kafka/transactions.html)

## 6. Retry 횟수는 어떤 기준으로 결정하는가?

### 면접 답변

“먼저 재시도로 해결될 오류인지 구분합니다. 일시적인 통신 장애라면 사용자가 기다릴 수 있는 시간, 외부 시스템의 복구 시간, 작업 비용을 기준으로 횟수와 간격을 정합니다. 잘못된 파일이나 인증 설정처럼 같은 요청으로 해결되지 않는 오류는 반복하지 않고 원인을 확인하도록 분리합니다.”

### 현재 구현과 목표의 차이

| 구분 | 현재 코드·기본값 |
|---|---|
| API Outbox | 총 8회 발행 시도, 실패 후 2·4·8·16·32·64·128초 대기, 한도 초과 시 `DEAD` |
| Worker Retry Topic | 최초 포함 총 5회, 재시도 간격 1·2·4·8초, 소진 시 `.dlt` |
| Worker 영구 오류 | `PermanentEncodingException`은 일반 재시도에서 제외 |
| 출력 업로드 완료 후 실패 | 체크포인트가 있으면 Callback-only 경로 사용 |

정책 문서의 ‘일반 외부 호출 10초→30초→2분→10분’, ‘Callback-only에 30분 추가’, ‘전체 인코딩 1분 뒤 한 번’은 단계별 정책 분리의 후속 목표다. 모두 현재 구현됐다고 설명하지 않는다. 실제 배포값은 환경 설정도 확인한다.

### 설계 시 함께 볼 것

- 최초 시도를 포함한 총 횟수인지, 추가 재시도 횟수인지 명시한다.
- 전체 소요 시간은 대기 시간 합계에 각 호출 시간과 큐 대기 시간이 더해진다.
- 대기 중 DB 트랜잭션을 유지하지 않는다. Outbox의 다음 실행 시각이나 Retry Topic으로 넘긴다.
- 라이브러리·Worker·Outbox의 재시도가 겹치면 실제 외부 호출 횟수가 늘 수 있다.
- 지수 백오프는 재시도 간격을 늘리는 방식이다. 동시 재시도 집중을 줄이는 jitter는 별도 설계 항목이며 현재 적용했다고 주장하지 않는다.
- 횟수를 정한 뒤 실패율·복구율·지연을 관측해 조정한다. 현재 숫자를 부하 실험으로 최적화한 값이라고 포장하지 않는다.

근거: [재시도 정책](../decisions/media-failure-handling-policy.md), [Outbox 엔티티](../../src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeOutbox.java). Worker 코드: `interfaces/kafka/EncodingRequestedConsumer.java`의 `@RetryableTopic`.

## 7. DLQ는 언제 사용하는가?

### 면접 답변

“재시도 한도를 넘었거나 같은 입력으로는 해결되지 않는 메시지를 정상 처리 흐름에서 분리할 때 사용합니다. 실패 메시지와 원인을 보관해 다른 작업의 처리를 이어가고, 원인을 수정한 뒤 필요한 건만 복구할 수 있게 합니다. Kafka에서는 보통 별도 Topic으로 구성해 DLT라고 부릅니다.”

### 프로젝트 사례

- Worker의 실패 메시지는 `media.encode.requested.dlt`에서 확인한다.
- DLT Handler는 원본 Topic·Partition·Offset과 실패 정보를 구조화 로그에 남기고, 가능한 작업의 실패 보고를 시도한다.
- Kafka에 발행하기 전 소진된 요청은 Kafka DLT가 아니라 DB Outbox의 `DEAD` 상태로 남는다.
- DLT에 이동했다고 API Job이 즉시 `FAILED`가 되는 것은 아니다. 실패 Callback 자체가 막힐 수 있다.

### 재처리 원칙

원인 수정 → Job·Inbox·Outbox 상태 확인 → 원본 파일·메시지 내용 확인 → 승인된 단건 재처리 → 최종 상태 확인 순서로 진행한다. 이미 `FAILED`인 API Job은 상태를 되돌리지 않고 새 요청·새 Job으로 처리하는 정책이다.

DLT는 영구 저장소가 아니다. 실제 Topic 보존 설정을 확인하고, 필요한 사고 기록은 보존 기간 안에 별도로 남긴다. offset을 커밋했다고 Topic의 메시지가 즉시 삭제되는 것도 아니다.

근거: [DEAD·DLT 재처리 절차](../operations/media-dead-letter-reprocessing.md), [Spring Kafka Retry Topic·DLT 동작](https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html)

## 8. 인코딩 Callback이 두 번 들어오면 어떻게 처리하는가?

### 면접 답변

“같은 작업의 완료 통지가 다시 오면 현재 상태와 출력 정보를 확인합니다. 이미 같은 결과로 완료됐다면 성공으로 응답하되 결과를 다시 저장하지 않습니다. 첫 통지는 서버에 반영됐지만 응답이 Worker에 도착하지 못할 수 있기 때문에, 정상 중복을 오류로 취급하지 않는 것이 중요합니다.”

### OnFilm의 완료 처리

1. 내부 Callback 인증과 요청 검증을 통과해야 한다.
2. `jobId`로 Job을 찾고 bucket·key·content type이 예상 출력과 일치하는지 확인한다.
3. 이미 `DONE`이면 기존 상태와 완료 시각을 유지하고 반환한다.
4. 미완료라면 출력 존재를 확인하고 Movie 결과와 `DONE`을 함께 저장한다.
5. 같은 `FAILED` 통지가 반복돼도 최초 실패 정보와 완료 시각을 유지한다.

### 구분할 두 종류의 중복

- 업무 중복: 같은 `jobId`의 정당한 재통지. 상태를 확인해 멱등 처리한다.
- 인증 재전송: 같은 nonce·서명을 그대로 반복한 요청. replay 방지에서 거절될 수 있으므로 Worker의 재시도는 새 인증 정보를 생성해야 한다.

동시에 두 완료 요청이 실행되면 `@Version` 충돌로 한 요청이 409를 받을 수 있다. 중복 Callback을 ‘언제나 둘 다 즉시 성공한다’고 설명하지 않는다. 결과 불일치 요청도 정상 중복으로 받아들이지 않는다.

근거: [Callback 서비스](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobInternalService.java), [완료 검증·반영](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCompletionTransactionService.java), [내부 인증 정책](../decisions/media-encode-job-outbox-policy.md)

## 9. Callback 순서가 바뀌면 어떻게 처리하는가?

### 면접 답변

“도착한 순서대로 상태를 덮어쓰지 않고, 현재 상태에서 허용된 변경인지 확인합니다. OnFilm은 완료와 실패를 최종 상태로 보고 서로 뒤집지 못하게 했습니다. 시작 통지를 놓쳤더라도 실제 완료 결과를 반영할 수 있도록 요청 상태에서 바로 완료되는 것은 허용했습니다.”

| 현재 상태 | 도착한 통지 | 처리 |
|---|---|---|
| `REQUESTED` | `PROCESSING`, `DONE`, `FAILED` | 검증 후 변경 가능 |
| `PROCESSING` | `PROCESSING` | 기존 상태 유지 |
| `PROCESSING` | `DONE`, `FAILED` | 검증 후 변경 가능 |
| `DONE` | `DONE` | 출력 검증 후 기존 결과 유지 |
| `FAILED` | `FAILED` | 기존 실패 결과 유지 |
| `DONE` | `FAILED` 또는 `PROCESSING` | 409, 변경 거부 |
| `FAILED` | `DONE` 또는 `PROCESSING` | 409, 변경 거부 |

### 한계와 트레이드오프

- 먼저 확정된 최종 상태를 보존하는 정책이지, 실제 사건의 시간 순서를 완벽히 복원하는 방식은 아니다.
- API가 timeout으로 `FAILED` 처리한 뒤 늦은 `DONE`이 와도 거절한다. 늦은 출력물 정리와 복구는 별도 절차가 필요하다.
- 다른 업무에서 최신 재고·가격을 반영해야 한다면 업무 버전이나 순서 번호가 필요할 수 있다. 현재 Job 상태 정책을 그대로 일반화하지 않는다.
- 같은 Kafka Partition 안의 기록 순서와 HTTP Callback 도착 순서는 다르다. Retry Topic을 사용하는 처리도 원래의 처리 순서를 유지하지 못할 수 있다.

근거: [Job 상태 전이](../../src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeJob.java), [오류 코드](../../src/main/java/com/onfilm/domain/common/error/ErrorCode.java), [Retry Topic의 순서 보장 한계](https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html)

## 10. Kafka Consumer Lag은 무엇인가?

### 면접 답변

“Consumer가 Topic에 쌓인 메시지를 얼마나 따라가지 못하고 있는지 보여주는 값입니다. Consumer group의 커밋 위치와 로그 끝 위치를 비교해 확인합니다. Lag가 계속 증가하면 유입량보다 처리량이 부족하거나 처리가 막혔을 가능성을 확인합니다.”

### 읽는 방법과 주의사항

- 일반적인 group 조회: `LOG-END-OFFSET - CURRENT-OFFSET`.
- 커밋 offset은 다음에 읽을 위치다. 끝 위치 100, 커밋 위치 90이면 offset 차이는 10이다.
- Lag는 시간 단위가 아니다. 작업당 처리 시간이 다르면 같은 Lag라도 예상 대기 시간이 다르다.
- 압축·트랜잭션 레코드 등으로 offset에 빈 구간이 있을 수 있어 항상 정확한 미처리 업무 건수와 일치하지는 않는다.
- Consumer 클라이언트의 `records-lag` 계열 지표는 현재 소비 위치 기준일 수 있다. group의 커밋 기준 Lag와 같은 값이라고 단정하지 않는다.
- 원본 Topic뿐 아니라 Retry·DLT와 Inbox 상태를 함께 확인한다. Lag 0은 업무 성공의 증거가 아니다.

### 실제 프로젝트 사례

AWS 검증에서 원본 Topic의 Lag는 0이었지만 Job은 `REQUESTED`였다. DLT의 메시지와 API 로그를 확인해 내부 Callback이 CSRF 필터에서 403으로 막힌 원인을 찾았다.

또한 프로세스가 종료되면 클라이언트 메트릭이 사라질 수 있다. ‘지표 없음’을 Lag 0이나 정상으로 해석하지 않고 target 상태·Consumer group을 확인한다.

근거: [장애 해결 기록](../problem-solving/12-production-callback-csrf-conflict.md), [Lag 관측 정책](../decisions/media-failure-handling-policy.md), [Kafka offset 설명](https://kafka.apache.org/40/design/design/)

## 11. 장애 발생 시 어떤 Log를 먼저 확인하는가?

### 면접 답변

“발생 시각과 영향받은 요청을 먼저 정하고, 해당 Job의 처리 흐름에서 마지막으로 성공한 단계와 처음 실패한 단계를 찾습니다. OnFilm에서는 jobId와 correlationId로 API, Outbox, Worker, Callback 로그를 연결합니다. 단순히 ERROR 로그만 찾지 않고 실패 직전의 상태 변경도 함께 확인합니다.”

### 증상에 따른 첫 확인 대상

| 증상 | 먼저 확인할 로그·상태 |
|---|---|
| 배포 후 기동 실패 | systemd 종료 원인, JVM 기동 오류, DB 연결·Flyway 오류 |
| 요청했는데 Worker가 받지 않음 | Job·Outbox 저장 여부, `MEDIA_ENCODE_OUTBOX_PUBLISH_FAILED`, `MEDIA_ENCODE_OUTBOX_DEAD` |
| 메시지는 받았지만 작업 실패 | `MEDIA_ENCODE_MESSAGE_CONSUMED`, `MEDIA_ENCODE_ATTEMPT_FAILED`, 실패 stage·errorCode |
| 출력은 있는데 API 상태가 그대로임 | Callback HTTP 상태·인증 실패, Inbox `OUTPUT_UPLOADED`·`FAILURE_PENDING` |
| 자동 재시도가 끝남 | `MEDIA_ENCODE_DLT_RECEIVED`, 원본 Topic·Partition·Offset |

### 프로젝트에서 말할 수 있는 경험

“Job이 REQUESTED에 멈췄을 때 Kafka의 offset과 DLT를 먼저 확인해 전달 이후 문제로 범위를 좁혔습니다. 그다음 Callback 오류를 따라가 운영 프로필의 CSRF 필터가 내부 요청을 막는 것을 찾았습니다. 수정 후에는 새 Job이 DONE까지 가는 전체 흐름을 확인했습니다.”

- 함께 보는 필드: `jobId`, `requestId`, `correlationId`, `eventType`, `stage`, `status`, `attempt`, `retryable`, `errorCode`.
- 기동 시각 이전의 장애라면 현재 부팅 로그만 조회하는 `journalctl -b`로는 놓칠 수 있다. 사고 시각을 포함한 보존 로그를 확인한다.
- 원문 토큰·인증 헤더·서명·비밀키·Presigned URL을 로그나 사고 문서에 옮기지 않는다.

근거: [Runbook 초동 대응](../operations/media-incident-runbook.md), [Callback 장애 사례](../problem-solving/12-production-callback-csrf-conflict.md)

## 12. 어떤 Metric을 먼저 확인하는가?

### 면접 답변

“서비스가 응답하는지, 실패율과 지연이 증가했는지 먼저 봅니다. 그다음 메시지 적체, Worker 단계별 처리 시간, DB 연결 대기와 자원 사용률을 확인해 병목을 좁힙니다. 전체 이상은 메트릭으로 찾고 개별 작업의 원인은 로그로 확인합니다.”

| 순서 | 확인 지표 | 판단할 내용 |
|---|---|---|
| 1. 가용성 | Prometheus `up`, health, 요청 수·5xx 비율 | 실제 장애인지, 수집 문제인지, 영향 범위 |
| 2. 사용자 지연 | API 응답 시간 p95·p99, Job 전체 소요 시간 | 어느 요청·업무가 느려졌는지 |
| 3. 전달 적체 | Outbox 상태별 건수·가장 오래된 PENDING, Consumer Lag, DLT 유입 | 발행·소비 중 어느 구간이 막혔는지 |
| 4. 처리 단계 | 인코딩 시도 실패율, download·transcode·upload·callback 시간 | 비용이 커지거나 실패하는 단계 |
| 5. 의존성과 자원 | Callback 실패·대기 시간, Hikari active·pending, CPU·힙·디스크 | DB 대기·외부 통신·자원 포화 가능성 |

### 프로젝트 메트릭 예시

- `media.encode.outbox.records`, `media.encode.outbox.oldest.pending.age`
- `media.encode.job.duration`, `media.encode.job.timeout`
- `media.encode.callback`: `applied`, `duplicate`, `conflict`, `error` 구분
- `media.encode.worker.stage.duration`, `media.encode.worker.failure`
- `media.encode.worker.inbox.oldest.failure.pending.age`, `media.encode.worker.stale.recovery`

위 이름은 Micrometer 등록 이름이다. Prometheus에서는 점이 밑줄로 바뀌고 Counter·Timer에 단위나 접미사가 붙으므로 실제 노출 이름을 확인한다. p95·p99의 Prometheus 계산에는 histogram 등 해당 수집 설정도 필요하다.

### 해석 시 주의사항

- Worker의 ‘처리 시도 실패율’과 최종 Job 실패율은 다르다. 재시도 후 성공할 수 있다.
- 요청이 거의 없는 상황에서 1건 실패로 비율이 크게 변할 수 있어 시간 구간과 최소 표본을 함께 본다.
- 상태 Gauge는 주기적으로 DB 스냅샷을 갱신하므로 즉시 일치하지 않을 수 있다. Counter도 DB 커밋 건수의 회계 원장으로 사용하지 않는다.
- `jobId`, `userId`, `requestId`처럼 계속 늘어나는 값은 메트릭 Tag 대신 로그에 남긴다. 이는 [Prometheus의 높은 Cardinality 방지 지침](https://prometheus.io/docs/practices/naming/)과도 일치한다.
- Alert rule·Dashboard 존재와 실제 알림 수신 채널 연결은 별개다. Runbook 기준 Slack·이메일 등의 연결은 후속 항목이다.

근거: [API 메트릭 코드](../../src/main/java/com/onfilm/domain/kafka/metrics/MediaEncodeMetrics.java), [메트릭 명세와 경보 정책](../decisions/media-failure-handling-policy.md), [Runbook 관측 범위](../operations/media-incident-runbook.md)

## 13. 배포 실패 시 어떻게 Rollback할 것인가?

### 면접 답변

“이전 배포 파일을 보관하고 새 버전의 상태 확인이 실패하면 이전 파일로 실행 대상을 되돌립니다. 다만 프로그램만 되돌린다고 DB와 이미 보낸 메시지까지 돌아가는 것은 아닙니다. 이전 버전이 현재 스키마와 메시지를 처리할 수 있는지 확인하고, 복구 후에는 핵심 요청과 비동기 작업까지 검증하겠습니다.”

### 현재 EC2 배포 방식

1. 커밋별 JAR를 `releases`에 보관한다.
2. 실행 중인 `current.jar` 링크의 실제 경로를 기록한다.
3. 새 JAR로 링크를 바꾸고 systemd 서비스를 재시작한다.
4. health가 `UP`인지 반복 확인한다.
5. 실패하면 최근 서비스 로그를 출력하고, 유효한 이전 파일이 있으면 링크를 되돌려 재시작한다. Workflow는 실패로 종료한다.

### 현재 자동화의 한계

- 이전 파일이 존재한다는 사실만으로 이전 버전의 정상 동작이 보장되지는 않는다.
- 현재 Workflow의 되돌리기 분기는 재시작까지 수행하며, 롤백 후 별도 health 재검증은 운영 확인 또는 후속 자동화가 필요하다.
- 첫 배포처럼 이전 릴리스가 없을 수 있다. Worker Workflow는 이 경우 서비스를 중지한다. API도 첫 배포 실패 시 현재 서비스 상태를 확인해야 한다.
- 환경 파일, DB 데이터, 외부 파일과 Kafka 메시지는 JAR 링크를 바꿔도 되돌아가지 않는다.
- health `UP`만으로 영상 처리 성공을 증명하지 못한다. 주요 API와 승인된 테스트 Job의 완료를 확인해야 한다.

### DB·메시지 변경이 함께 있을 때의 대응

- 구버전과 호환되는 컬럼 추가부터 적용하고, 사용 전환과 제거는 나중에 분리하는 방식으로 롤백 가능성을 확보한다.
- Flyway 이력이나 이미 적용한 Migration을 삭제·수정해서 맞추지 않는다. 호환되지 않으면 수정 배포나 검증된 복원 절차를 선택한다.
- 발행된 메시지는 구버전 Consumer가 읽을 수 있는지 확인한다. offset을 임의로 되돌리거나 DLT를 일괄 재투입하지 않는다.
- Worker 재시작 후에는 Inbox lease와 Callback 복구 상태, Lag 감소, 최종 Job 결과까지 확인한다.

근거: [API 배포 Workflow](../../.github/workflows/api-ci.yml), [Runbook 복구 기준](../operations/media-incident-runbook.md), [DB 변경 규칙](../../AGENTS.md). Worker 배포 근거는 별도 저장소 `.github/workflows`의 릴리스 활성화 단계다.

## 14. Topic, Partition, Offset은 각각 무엇인가?

### 면접 답변

“Topic은 같은 종류의 메시지를 모으는 단위이고, Partition은 Topic의 데이터를 나누어 저장하고 병렬로 처리하는 단위입니다. Offset은 한 Partition 안에서 레코드의 위치를 나타냅니다. 따라서 Offset 숫자만으로 메시지를 식별할 수는 없고 Topic과 Partition도 함께 알아야 합니다.”

### 프로젝트에 연결하기

- 인코딩 요청 Topic과 Retry·DLT Topic은 서로 다른 처리 경로다.
- DLT 로그에 원본 Topic·Partition·Offset을 함께 기록해 실패한 레코드를 찾는다.
- Partition 수와 복제본 수는 다르다. Partition은 분산 단위이고 복제본은 같은 Partition 데이터를 다른 Broker에도 유지하는 것이다.
- Offset은 Job의 업무 번호가 아니다. 같은 Job이 재발행되면 다른 Offset을 가질 수 있다.

근거: [Kafka 설계](https://kafka.apache.org/40/design/design/), [DLT 조사 절차](../operations/media-dead-letter-reprocessing.md)

## 15. 메시지 Key는 어떻게 정하고, 순서 보장은 어디까지 가능한가?

### 면접 답변

“함께 순서를 지켜야 하는 업무 단위를 기준으로 Key를 정합니다. 기본적인 키 기반 분배에서는 같은 Key가 같은 Partition으로 가므로 그 안의 기록 순서를 활용할 수 있습니다. 다만 다른 Partition 사이의 전체 순서나 별도 스레드·Retry Topic에서의 처리 완료 순서까지 보장하는 것은 아닙니다.”

### OnFilm의 선택과 한계

- Producer는 `jobId`를 Key로 보낸다. 같은 작업을 추적하기 위한 기준이며 중복 제거는 Inbox가 담당한다.
- 같은 Movie에 서로 다른 Job 두 개가 생기면 Key가 다르다. 현재 선택을 ‘Movie의 모든 변경 순서 보장’으로 설명하지 않는다.
- 동일 Movie의 최신 작업만 반영해야 한다면 별도의 업무 버전·활성 Job 검사 등을 검토해야 한다. Key만 바꿔서는 늦은 Callback 덮어쓰기 문제까지 해결되지 않는다.
- 기본 해시 분배에서 Partition 수를 늘리면 같은 Key의 목적지가 달라질 수 있다. 변경 전후 레코드의 처리 순서도 검토한다.
- 특정 Key에 요청이 몰리는 Hot Partition은 다른 Partition에 여유가 있어도 병목이 된다.

근거: [메시지 Key를 설정하는 Producer](../../src/main/java/com/onfilm/domain/kafka/producer/KafkaMediaEncodeJobProducer.java), [Retry Topic의 순서 제한](https://docs.spring.io/spring-kafka/reference/retrytopic/how-the-pattern-works.html)

## 16. Consumer Group은 무엇이며, Worker를 늘리면 무조건 빨라지는가?

### 면접 답변

“Consumer Group은 같은 Topic의 처리를 나누어 맡는 Consumer들의 묶음입니다. 일반적인 Consumer Group에서는 한 Partition을 그룹 안의 한 Consumer가 담당합니다. 따라서 Worker를 늘려도 Partition 수를 넘으면 해당 Topic을 배정받지 못하는 Consumer가 생길 수 있고, DB나 CPU가 병목이면 처리량이 늘지 않을 수 있습니다.”

### 예시와 프로젝트 주의사항

- Partition 3개인 단일 Topic에 Consumer 1개면 그 Consumer가 3개를 담당할 수 있다. Consumer가 4개라면 하나는 해당 Topic의 Partition을 받지 못할 수 있다.
- 같은 업무의 부하 분산에는 같은 `groupId`를 쓴다. 서버마다 다른 `groupId`를 주면 독립 구독이 되어 각 그룹이 같은 메시지를 받는다.
- Worker의 기본 Listener concurrency는 1이다. 하지만 Retry Topic Consumer와 복구 스케줄러도 있으므로 ‘JVM 전체에서 인코딩이 항상 하나만 실행된다’는 뜻은 아니다.
- 확장 전 공유 Inbox, 전체 FFmpeg 동시 실행 수, CPU·디스크·DB Pool 한도를 확인한다.

여기서는 OnFilm이 사용하는 일반 Consumer Group을 설명한다. 별도 모델인 Share Group과 혼동하지 않는다.

근거: [Kafka Consumer 설정](https://kafka.apache.org/40/configuration/consumer-configs/), [Worker 실행 정책](../decisions/media-failure-handling-policy.md)

## 17. Rebalance는 왜 발생하며 어떤 문제가 생길 수 있는가?

### 면접 답변

“Consumer가 추가되거나 종료되는 등 그룹 구성이 바뀌면 Partition 담당을 다시 배정할 수 있습니다. 이를 Rebalance라고 합니다. 배정이 바뀌는 동안 처리가 지연될 수 있고, 이전 Consumer가 처리했지만 커밋하지 못한 레코드를 새 Consumer가 다시 처리할 수 있습니다.”

### 점검할 내용

- 배포·장애·구독 변경과 Rebalance 시각이 일치하는지 확인한다.
- 반복 재할당이면 처리 지연, 긴 GC, 네트워크 문제, poll 간격 초과 등을 조사한다.
- 중복을 막기 위해 새 담당 Consumer도 반드시 Inbox를 확인해야 한다.
- 프로토콜·할당 전략에 따라 재할당 범위가 다르므로 모든 Rebalance가 항상 그룹 전체를 완전히 멈춘다고 단정하지 않는다.

근거: [Kafka Consumer 설정](https://kafka.apache.org/40/configuration/consumer-configs/), [Worker 장애 복구 절차](../operations/media-incident-runbook.md)

## 18. Heartbeat와 max.poll.interval.ms는 어떻게 다른가?

### 면접 답변

“Heartbeat는 Consumer가 그룹과 통신하며 살아 있는지를 알리는 신호이고, max.poll.interval은 메시지를 가져오는 poll을 너무 오래 호출하지 않는 상황을 감지하는 기준입니다. 통신이 살아 있어도 업무 처리가 오래 걸리면 poll 간격을 넘을 수 있어, 긴 작업에서는 두 조건을 구분해서 봐야 합니다.”

### 프로젝트 사례와 대안

- Worker 기본 `max-poll-records`는 1이고 `max.poll.interval.ms`는 10,800,000ms, 즉 3시간이다.
- 한 번에 한 건만 받아도 그 작업이 3시간을 넘으면 문제가 생길 수 있다. 다운로드·업로드·Callback 시간을 포함해 계산한다.
- 설정을 지나치게 늘리면 멈춘 처리를 발견하는 시간이 길어진다. 정답인 고정 숫자는 없다.
- Kafka 수신과 실제 실행을 분리해 DB에 작업을 안전하게 맡긴 뒤 offset을 진행하는 대안도 있다. 이 경우 실행 스케줄링·복구·동시성 관리가 추가된다.
- Consumer를 임의 스레드 풀에 던지고 Listener만 빨리 반환하는 것은 별개다. 실행 결과를 저장하기 전에 offset이 진행되지 않도록 설계해야 한다.

Heartbeat·세션 설정의 제어 위치와 재할당 시점은 그룹 프로토콜·정적 멤버십에 따라 달라진다. [Kafka Consumer 설정](https://kafka.apache.org/40/configuration/consumer-configs/)

## 19. acks=all이면 메시지 유실이 완전히 방지되는가?

### 면접 답변

“아닙니다. acks는 Producer가 어느 수준의 Broker 확인을 기다릴지 정하는 설정입니다. all은 동기화된 복제본 집합의 확인을 기다리지만, 복제본이 하나뿐이면 다른 Broker에 복사되는 것은 아닙니다. 복제 수와 최소 동기화 복제본 수, 장애 시 리더 선출 정책을 함께 봐야 합니다.”

### 설정의 의미와 트레이드오프

- `acks=0`: Broker 응답을 기다리지 않는다.
- `acks=1`: Leader의 기록 확인을 기다린다.
- `acks=all`: ISR, 즉 동기화된 복제본 집합의 확인을 기다린다.
- 예를 들어 복제 수 3, 최소 ISR 2로 쓰기 조건을 강화하면 안전성을 높이는 대신 복제본 부족 시 쓰기가 실패할 수 있다.

API의 `KafkaProducerConfig`는 직접 만든 설정 Map에 `acks`나 `enable.idempotence`를 명시하지 않는다. 따라서 ‘코드에서 명시적으로 보장했다’고 설명하지 않고 실제 클라이언트 버전의 기본값과 실행 설정을 확인한다. 직접 만든 Factory에 임의의 Spring 설정이 자동 반영된다고 가정하지 않는다.

성능 측정을 위해 구성했던 단일 Broker·복제 수 1 환경은 다중 Broker 장애 내구성 검증과 구분한다.

근거: [Producer 설정 코드](../../src/main/java/com/onfilm/domain/kafka/config/KafkaProducerConfig.java), [Kafka acks·멱등 전송 설정](https://kafka.apache.org/40/configuration/producer-configs/)

## 20. KafkaTemplate.send()가 반환됐으면 발행에 성공한 것인가?

### 면접 답변

“send는 비동기 결과를 반환하므로 메서드가 반환됐다는 사실만으로 발행 성공이라고 볼 수 없습니다. 반환된 Future의 성공·실패를 확인해야 합니다. OnFilm Publisher는 결과를 기다려 발행 확인을 받은 뒤 Outbox를 PUBLISHED로 변경합니다.”

### 프로젝트 사례와 중요한 한계

- `KafkaMediaEncodeJobProducer.send()`는 `CompletableFuture<SendResult<...>>`를 반환한다.
- Publisher는 `.get(30, TimeUnit.SECONDS)`로 기다린다. 이 대기는 DB 트랜잭션 밖이지만 Publisher 스레드는 점유한다.
- 기다리다 timeout이 나도 Kafka 전송이 취소됐거나 실패했다고 확정할 수는 없다. 나중에 전송이 성공할 수도 있어 Outbox 재발행과 중복 처리가 필요하다.
- Future 성공은 해당 Producer 설정 수준의 발행 확인이지 Worker의 처리 완료가 아니다.
- 직렬화·메타데이터·버퍼 상황에 따라 send 호출 자체도 즉시 실패하거나 잠시 막힐 수 있다. Future 대기 30초가 메서드 전체의 절대 상한이라고 단정하지 않는다.

근거: [Producer](../../src/main/java/com/onfilm/domain/kafka/producer/KafkaMediaEncodeJobProducer.java), [Publisher의 발행 확인](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeOutboxPublisher.java), [Kafka Producer timeout 설정](https://kafka.apache.org/40/configuration/producer-configs/)

## 21. 영상 처리를 동기 HTTP 호출이 아니라 비동기로 분리한 이유는?

### 면접 답변

“영상 처리는 오래 걸리고 CPU 사용량이 커서 사용자 요청을 처리하는 서버와 역할을 분리했습니다. API는 작업을 접수하고 상태를 관리하며, Worker가 실제 영상 처리를 수행합니다. 대신 즉시 결과를 돌려줄 수 없으므로 작업 조회와 실패 복구 기능이 필요합니다.”

### 트레이드오프

- HTTP 202는 작업을 접수했다는 뜻이지 영상 처리가 성공했다는 뜻이 아니다.
- Kafka는 일시적인 유입 증가를 버퍼링하지만 Worker의 실제 처리 능력을 늘려주지는 않는다.
- 단순하고 짧은 작업까지 항상 Kafka로 분리할 필요는 없다. 지연 허용 범위·독립 확장 필요·운영 비용을 보고 선택한다.
- OnFilm도 Outbox·Inbox·재시도·관측성이라는 관리 비용을 추가로 부담한다.

근거: [비동기 인코딩 개선 사례](../problem-solving/05-media-encode-outbox-and-worker.md)

## 22. 요청이 처리 능력보다 빠르게 들어오면 어떻게 대응하는가?

### 면접 답변

“대기열이 있다는 이유로 무제한으로 받지 않고, 유입률과 완료율, 가장 오래 기다린 작업을 함께 확인합니다. 처리 자원을 늘릴 수 있는지 판단하고 필요하면 요청량이나 동시 실행 수를 제한합니다. 특히 영상 처리는 Worker를 늘릴 때 CPU뿐 아니라 디스크와 외부 저장소 부하도 봐야 합니다.”

### 판단 순서

1. Lag·Outbox age·Job 완료 시간을 통해 적체 구간을 찾는다.
2. 처리 단계별 시간으로 CPU, 저장소, Callback 중 병목을 구분한다.
3. Partition과 외부 시스템 용량이 허용하는 범위에서 확장한다.
4. 사용자별 제출 한도, 대기열 상한, 동시 실행 제한과 초과 시 응답 정책을 검토한다.

4번은 보완 방향이다. 현재 프로젝트에 모든 형태의 유입 제한이 구현됐다고 설명하지 않는다. 적체가 길어져 Kafka 보존 기간이나 API Job timeout을 넘지 않는지도 확인한다.

근거: [적체 대응 Runbook](../operations/media-incident-runbook.md)

## 23. Timeout, Backoff, Job Timeout은 어떻게 구분하는가?

### 면접 답변

“호출 Timeout은 한 번의 응답을 얼마나 기다릴지, Backoff는 실패 후 다음 시도까지 얼마나 기다릴지 정합니다. Job Timeout은 접수부터 종료까지 업무 전체를 얼마나 기다릴지 정하는 기준입니다. 각 시간을 따로 정하되 큐 대기와 재시도 시간을 포함해 서로 충돌하지 않게 해야 합니다.”

### OnFilm의 시간 관계

- FFmpeg 기본 제한: 2시간
- Inbox 처리 lease: 3시간
- Kafka poll 간격 제한: 현재 기본 3시간, 정책 목표는 4시간
- API Job timeout: 4시간 30분

이 값들은 서로 다른 구간을 측정한다. FFmpeg 2시간에는 큐 대기·다운로드·업로드가 포함되지 않으므로 단순한 대소 비교만으로 안전성이 보장되지는 않는다. 정상 처리의 상위 지연값과 전체 재시도 예산을 기준으로 검증해야 한다.

Timeout은 ‘상대 작업이 실패하거나 중단됐다’는 확정 통지가 아니다. 늦은 성공·늦은 Callback·외부 작업의 취소 여부를 별도로 처리한다.

근거: [시간 제한의 현재값·목표값](../decisions/media-failure-handling-policy.md)

## 24. Circuit Breaker는 Retry와 무엇이 다른가?

### 면접 답변

“Retry는 실패한 요청을 다시 시도하는 것이고, Circuit Breaker는 장애가 반복되는 상대에게 요청을 계속 보내지 않도록 잠시 차단하는 것입니다. 외부 장애가 우리 스레드와 연결 자원까지 소모하는 상황을 줄일 수 있지만, 데이터 복구나 재처리를 대신하지는 않습니다.”

- Closed: 호출을 보내며 실패·지연을 관측한다.
- Open: 호출을 빠르게 거절한다.
- Half-open: 제한된 시험 호출로 복구 여부를 판단한다.

OnFilm은 Circuit Breaker를 필수 초기 구성으로 도입하지 않았다. Callback 장애가 자원 고갈로 이어지는 것이 관측되면 전용 적용을 검토한다. 차단 중인 작업은 성공 처리하지 않고 저장한 상태로 재시도·운영 확인 대상에 남겨야 한다.

동시 호출 수를 제한하는 Bulkhead, 한 번의 대기를 제한하는 Timeout과도 다른 기능이다. 이름이 다른 기능을 하나의 장치로 해결했다고 설명하지 않는다.

근거: [현재 도입 정책](../decisions/media-failure-handling-policy.md), [Resilience4j CircuitBreaker 설명](https://resilience4j.readme.io/docs/circuitbreaker)

## 25. Lease와 DB Lock은 어떻게 다르며, Fencing은 왜 필요한가?

### 면접 답변

“DB Lock은 트랜잭션 중 동시 변경을 조율하고, lease는 트랜잭션이 끝난 뒤에도 일정 시간 작업 권한을 표시하는 방식입니다. 하지만 lease가 만료돼도 이전 Worker의 실행이 자동으로 멈추지는 않습니다. 이전 작업자의 늦은 저장까지 막으려면 권한 세대 번호를 검증하는 Fencing 같은 장치가 필요할 수 있습니다.”

### 개념 예시와 프로젝트 한계

1. Worker A가 처리 권한을 얻은 뒤 오래 멈춘다.
2. lease가 만료되어 Worker B가 새 권한으로 작업한다.
3. A가 다시 실행되어 뒤늦게 결과를 저장하려 한다.
4. 저장 대상이 권한 세대 번호를 비교한다면 A의 오래된 쓰기를 거부할 수 있다.

- OnFilm은 lease·행 잠금·상태 검증으로 선점과 복구를 관리한다.
- 현재 완료 호출에 처리 권한 세대 번호를 전달해 검증하는 전역 Fencing은 구현됐다고 주장하지 않는다. `@Version`도 이전 작업자가 새로 읽은 행에 쓰는 모든 상황을 자동 방어하지는 않는다.
- heartbeat로 lease를 연장하면 정상 장시간 작업의 오인 회수를 줄일 수 있지만, 이전 작업자의 늦은 외부 쓰기 문제와 동일한 해결책은 아니다.
- Fencing은 DB뿐 아니라 실제 부작용을 반영하는 저장 대상에서도 검증돼야 의미가 있다.

근거: [Outbox 선점 코드](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeOutboxTransactionService.java), [Worker 복구·heartbeat 후속 정책](../decisions/media-failure-handling-policy.md)

## 26. Worker가 죽으면 Kafka 재전달만으로 충분히 복구되는가?

### 면접 답변

“항상 충분하지는 않습니다. 메시지가 다른 경로로 이동했거나 현재 처리 위치가 이미 진행된 상황도 있어, DB에 남은 작업 상태를 함께 확인해야 합니다. OnFilm은 만료된 Inbox를 찾는 복구 스케줄러를 두고, 출력 업로드까지 완료된 작업은 영상 변환을 생략하고 Callback부터 재개합니다.”

### 현재 복구 경로와 주의사항

- `StaleInboxRecoveryService`는 lease가 만료된 `PROCESSING`·`OUTPUT_UPLOADED` 작업을 조회한다.
- 조회 결과만으로 실행을 확정하지 않고 Processor에서 다시 선점한다.
- 실패 통지 대기는 별도 `FAILURE_PENDING` 경로로 처리한다.
- 업로드 직후 DB 체크포인트 저장 전에 종료되면 업로드를 했는지 기록으로 확정하지 못할 수 있다. 모든 중간 작업을 재실행 없이 복원하는 구조는 아니다.
- Retry Topic의 총 5회 설정이 복구 스케줄러·수동 재처리까지 합한 생애 전체 시도 상한은 아니다. 전역 재시도 예산이 필요하면 별도 제어와 검증이 필요하다.

근거: [복구 Runbook](../operations/media-incident-runbook.md). 코드 확인 위치: Worker의 `StaleInboxRecoveryService`, `InboxTransactionService`, `EncodingJobProcessor`.

## 27. Worker 배포 시 Graceful Shutdown은 어떻게 설계하는가?

### 면접 답변

“새 작업을 받는 것을 멈추고 진행 중인 작업이 안전하게 마무리되도록 제한된 시간을 줍니다. 그 시간 안에 끝나지 않는 긴 작업은 프로세스 종료 후에도 복구할 수 있어야 합니다. 영상 처리는 배포마다 몇 시간씩 기다리기 어려우므로 체크포인트와 lease 복구가 함께 필요합니다.”

### 확인할 항목

- 종료 대기 시간, Kafka Listener 정지, 스케줄러의 신규 선점 중지 순서
- 실행 중인 FFmpeg 자식 프로세스의 종료와 임시 파일 처리
- 진행 중 결과의 DB 저장·offset 커밋 여부와 재시작 시 복구
- 종료 중 Callback의 성공 여부가 불확실할 때의 중복 처리

Worker에는 Spring 종료 대기 설정이 있지만, 그 설정만으로 모든 장시간 작업의 정상 종료가 보장되지는 않는다. Listener·스케줄러·systemd 제한과 자식 프로세스를 함께 점검하고 실제 종료 실험으로 확인해야 한다. 이러한 종료 실험을 모두 이번에 수행한 것은 아니다.

근거: [Worker 종료 장애의 대응 기준](../operations/media-incident-runbook.md)

## 28. 메시지 형식이 바뀔 때 API와 Worker를 어떻게 배포하는가?

### 면접 답변

“새 코드끼리만 통신하는 것이 아니라 Kafka와 Outbox에 남은 이전 메시지도 읽어야 합니다. 따라서 기존 필드의 의미를 유지하고 새 필드를 선택적으로 추가하는 등 호환성을 먼저 검토합니다. 호환되지 않는 변경이라면 버전별 처리와 배포 순서를 정해야 합니다.”

### 현재 구현과 확장 방향

- 메시지에 `schemaVersion`을 두고 API Publisher와 Worker에서 지원 버전을 검사한다.
- Worker는 Key와 `jobId` 일치, 작업 종류·preset·파일 위치·content type도 검증한다.
- 현재 버전 검사는 지원하지 않는 형식을 거절하는 장치다. 여러 공급사의 형식을 자동 변환하거나 모든 이전 버전을 처리하는 기능이 아니다.
- 호환 Consumer를 먼저 배포한 뒤 Producer를 전환하는 방안을 검토한다. 롤백할 Consumer도 새 메시지를 읽을 수 있는지 확인한다.
- Inbox가 저장 payload 문자열을 비교하므로 필드 추가·직렬화 변화가 같은 작업의 재전달 판정에 미치는 영향도 테스트한다.
- 원본 Topic뿐 아니라 Retry·DLT·Outbox·Inbox의 보존 데이터까지 배포 호환성 범위에 넣는다.

근거: [Publisher 버전 검사](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeOutboxPublisher.java), [메시지 정책](../decisions/media-encode-job-outbox-policy.md). Worker 코드: `EncodeRequestValidator`, `MediaEncodeInbox`.

## 29. JSON이 깨져 Listener에 들어오기 전부터 실패하면 어떻게 하는가?

### 면접 답변

“업무 처리 중 예외와 메시지를 객체로 읽는 단계의 예외를 구분해야 합니다. 파싱 자체가 실패하면 Listener 안의 예외 처리나 jobId 기반 로그가 실행되지 않을 수 있습니다. 원본 레코드 위치를 남기고 오류 처리기와 DLT 경로가 이런 입력도 처리하는지 확인하겠습니다.”

### OnFilm 기준의 주의사항

- Worker는 문자열 역직렬화와 `StringJsonMessageConverter`를 사용한다. Kafka 바이트 해석, JSON 변환, 도메인 검증은 서로 다른 실패 지점이다.
- 현재 `@DltHandler`도 타입이 지정된 메시지를 받는다. null 검사 코드가 있다고 모든 잘못된 JSON이 안전하게 이 Handler에 도착한다고 단정할 수 없다.
- 잘못된 JSON, 잘못된 필드 타입, 누락된 식별자를 실제 Consumer 경로에 넣어 원본 보존·로그·격리 여부를 검증해야 한다.
- 식별자를 해석하지 못하면 Topic·Partition·Offset으로 먼저 추적한다. 원문 전체를 로그에 무조건 출력하지 않는다.

이 항목은 추가 검증 대상이며 ‘모든 역직렬화 실패 대응 완료’ 사례로 사용하지 않는다.

근거: Worker의 `AppConfig`, `EncodingRequestedConsumer`; [기존 장애 주입 범위](../decisions/media-failure-handling-policy.md)

## 30. auto.offset.reset=earliest이면 재시작할 때 항상 처음부터 읽는가?

### 면접 답변

“아닙니다. 유효한 커밋 위치가 있으면 그 위치를 기준으로 이어서 읽습니다. auto.offset.reset은 처음 사용하는 그룹처럼 커밋 위치가 없거나, 보존 기간이 지나 기존 위치를 읽을 수 없을 때 적용하는 정책입니다. earliest도 삭제된 레코드까지 복구하는 것은 아닙니다.”

### 프로젝트와 운영 주의사항

- Worker의 기본값은 `earliest`다. 새로운 `groupId`로 실행하면 보존 중인 과거 메시지가 대량으로 다시 들어올 수 있다.
- `latest`는 reset이 필요한 상황에서 끝 위치부터 시작하므로 과거 대기 작업을 건너뛸 수 있다.
- 의도적인 offset reset은 대상 그룹·Partition·범위와 중복 영향을 확인한 뒤 승인된 절차로 수행한다.
- Inbox 기록을 이미 정리했다면 오래된 메시지 재생 시 중복 판별이 달라질 수 있다. 메시지와 멱등성 기록의 보존 기간을 함께 설계한다.

근거: [Kafka auto.offset.reset](https://kafka.apache.org/40/configuration/consumer-configs/), [재처리 절차](../operations/media-dead-letter-reprocessing.md)

## 31. Outbox를 주기적으로 조회하는 방식과 CDC는 어떤 차이가 있는가?

### 면접 답변

“Polling은 애플리케이션이 DB에서 발행할 행을 주기적으로 찾는 방식이고, CDC는 DB의 변경 기록을 읽어 변화를 전달하는 방식입니다. Polling은 시작하기 단순하지만 조회 부하와 주기만큼의 지연이 생깁니다. CDC는 변경 전달에 유리할 수 있지만 Connector와 변경 로그 보존 등 운영 대상이 늘어납니다.”

### OnFilm의 선택

- 현재는 스케줄 기반 Polling과 DB 선점을 사용한다.
- Polling 주기·조회 인덱스·한 번에 선점하는 개수·발행 시간·lease 길이를 함께 검토한다.
- 여러 건을 먼저 선점하고 순차 전송한다면 뒤에 있는 건이 실행 전에 lease를 소진할 수 있다. 배치 크기를 늘리는 것이 항상 처리량 개선은 아니다.
- 규모가 커지면 CDC 기반 Outbox Relay를 검토할 수 있지만, CDC를 선택해도 소비자의 멱등성과 재처리 설계는 필요하다.

CDC는 현재 프로젝트의 도입 성과가 아니라 대안이다.

근거: [Polling 선택 이유와 대안](../decisions/media-encode-job-outbox-policy.md), [Publisher](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeOutboxPublisher.java)

## 32. Liveness, Readiness, 업무 성공 여부는 어떻게 다른가?

### 면접 답변

“Liveness는 프로세스가 살아서 복구 가능한 상태인지, Readiness는 새 요청을 받을 준비가 됐는지 판단하는 신호입니다. 하지만 둘 다 개별 영상 처리의 성공을 증명하지는 않습니다. 실제 업무 완료는 Job 상태와 완료 지연·실패율로 따로 확인해야 합니다.”

### 프로젝트에 적용해 설명하기

- Worker의 사용자 정의 health는 작업 디렉터리 쓰기 권한·여유 공간과 절대 경로로 지정한 실행 파일의 실행 가능 여부 등을 확인한다.
- 이 검사는 실제 FFmpeg 변환·S3 업로드·Callback 왕복 테스트를 대신하지 않는다.
- health 응답에 readiness 그룹이 보인다고 모든 외부 의존성 검사가 포함됐다고 가정하지 않는다. 그룹 구성과 호출 결과를 확인한다.
- Kafka Worker의 HTTP readiness가 바뀐다고 Consumer가 자동으로 소비를 멈추는 것도 아니다. Listener 제어가 연결돼 있는지 확인해야 한다.

외부 DB 장애를 Liveness 실패로 연결하면 모든 인스턴스를 반복 재시작하는 문제가 생길 수 있다. 외부 의존성을 Readiness에 포함할지도 영향 범위를 보고 판단한다. [Spring Boot의 가용성 검사 지침](https://docs.spring.io/spring-boot/4.2/reference/actuator/endpoints.html)

근거: [Runbook 정상화 기준](../operations/media-incident-runbook.md). Worker 코드: `WorkerHealthIndicator`.

## 33. 평균 응답 시간만 보면 안 되는 이유와 p95 집계 주의사항은?

### 면접 답변

“평균은 일부 느린 요청을 가릴 수 있어 p95·p99 같은 상위 지연도 봅니다. p95는 관측한 요청의 약 95%가 그 시간 이내에 끝났다는 뜻입니다. 여러 인스턴스의 p95를 단순 평균하면 전체 요청의 p95가 되지 않으므로 적절한 분포 데이터를 합쳐야 합니다.”

### 관측 시 주의사항

- 요청량·성공률·시간 구간이 다른 결과를 응답 시간 하나로 비교하지 않는다.
- Histogram은 구간별 누적 관측값으로 분위수를 추정할 수 있다. 구간 설정에 따른 오차가 있다.
- Summary의 인스턴스별 분위수를 평균해 전체 분위수로 사용하지 않는다.
- 인코딩은 영상 길이·preset·단계별로 소요 시간이 달라 성공 요청과 실패 요청의 분포도 구분한다.
- API 응답 시간과 Job 전체 완료 시간은 서로 다른 지표다. API가 빨라도 큐에서 오래 기다릴 수 있다.

근거: [Prometheus Histogram·Summary 지침](https://prometheus.io/docs/practices/histograms/), [프로젝트 메트릭 명세](../decisions/media-failure-handling-policy.md)

## 34. SLI, SLO, Alert는 어떻게 구분하고 복구 완료는 무엇으로 판단하는가?

### 면접 답변

“SLI는 성공률이나 처리 시간처럼 서비스 수준을 측정하는 지표이고, SLO는 그 지표에 대해 정한 목표입니다. Alert는 목표 위반이나 장애 징후를 운영자에게 알리는 수단입니다. 복구는 경보가 사라졌다는 이유만으로 선언하지 않고 실제 요청과 대기 작업이 정상 처리되는지 확인하겠습니다.”

### OnFilm에서 목표를 정한다면

- API 성공률과 영상 작업의 최종 성공률을 분리한다.
- 작업 완료 시간에는 접수 후 큐 대기·재시도 시간을 포함할지 명시한다.
- 사용자 입력 오류, 시스템 오류, 아직 진행 중인 작업을 분모·분자에서 어떻게 구분할지 정한다.
- ‘최근에 완료된 작업만’으로 성공률을 계산하면 계속 멈춘 작업이 빠질 수 있으므로 오래된 미완료 작업도 확인한다.
- 기간·표본·측정 조건을 합의한 뒤 목표를 정한다. 현재 Alert 임계값을 검증된 SLA나 실서비스 SLO라고 주장하지 않는다.

### 복구 완료 확인

프로세스·수집 상태 안정 → 신규 오류 감소 → 적체와 오래된 작업 감소 → 핵심 요청·Callback 정상 반영 → 남은 DEAD·DLT의 처리 계획 기록 순서로 확인한다. 재발 방지를 위해 원인, 시간순 대응, 놓친 경보와 회귀 테스트를 남긴다.

근거: [Google SRE의 SLI·SLO 설명](https://sre.google/sre-book/service-level-objectives/), [Runbook 복구 기준](../operations/media-incident-runbook.md), [장애 대응 개선 사례](../problem-solving/10-media-failure-observability-and-runbook.md)

## 복습 순서와 검증 근거

1. 중복 전달 → At-least-once → Inbox → 멱등성 → Outbox와 트랜잭션 경계
2. Retry → DLT → 중복 Callback → 상태 전이
3. Lag → 로그 → 메트릭 → 배포 롤백
4. Topic·Key·Group → Rebalance·poll → 발행 확인·내구성
5. 처리량 제한 → Timeout·Circuit Breaker → lease·복구·종료
6. 메시지 호환성·파싱 실패 → offset·보존 → Polling 대안
7. 가용성 검사 → 지연 분포 → 서비스 수준 목표·복구 판단

추가 질문 중 먼저 연습할 항목은 15~20번의 Kafka 동작 원리, 23~26번의 실패·복구 경계, 28번의 메시지 호환성이다. 모든 질문에서 현재 구현과 보완 방향을 구분해 답한다.

장애 주입 테스트의 범위는 [장애 대응 정책의 검증 절](../decisions/media-failure-handling-policy.md)과 [장애 대응·관측성 개선 사례](../problem-solving/10-media-failure-observability-and-runbook.md)를 참고한다. DB·상태 전이의 자동화 검증과 실제 Kafka 단절·S3 지연·OS 수준 Worker 종료 실험은 구분해서 설명한다.

이번 작업은 문서 작성과 코드·자료 확인이며 테스트나 원격 배포를 실행한 것은 아니다.

관련 단원: [Java·Spring·JPA](08-java-spring-jpa-interview-qa.md), [DB·동시성](09-db-concurrency-interview-qa.md), [인증·보안](10-auth-security-interview-qa.md)
