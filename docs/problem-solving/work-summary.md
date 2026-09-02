# OnFilm 주요 리팩토링 작업 요약

- 최초 작성일: 2026-09-02
- 최종 갱신일: 2026-09-02
- 목적: 여러 문제 해결 사례의 핵심을 한 문서에서 연속해서 읽을 수 있도록 정리

이 문서는 구현 세부사항보다 문제, 선택한 구조, 트레이드오프와 검증 결과를 빠르게 파악하기 위한 요약본이다. 상세한 코드 변경과 대안은 각 절의 관련 사례 문서를 참고한다.

## 1. 아키텍처와 에러 정책 개선

이번 작업의 목표는 기능이 늘면서 하나의 서비스에 섞인 조회·변경·외부 작업 책임을 분리하고, 계층마다 다르게 처리되던 예외를 하나의 일관된 API 오류 정책으로 통합하는 것이었다.

기존에는 `PersonReadService` 같은 이름의 서비스가 Person 조회뿐 아니라 Project·Scene·Card 생성과 변경, 영화 파일 삭제까지 담당했다. Controller와 Service가 Entity Collection을 직접 수정하거나 연관관계 연결 순서를 알고 있었고, DB 변경·Storage 작업·Kafka 발행·BCrypt 연산이 하나의 긴 Transaction에 포함되기도 했다. 서비스 이름만으로는 읽기인지 쓰기인지, 어느 Aggregate를 변경하는지, 외부 작업이 어느 시점에 실행되는지 파악하기 어려웠다.

이를 개선하기 위해 조회는 `QueryService`, 상태 변경은 `CommandService`로 분리했다. Query는 read-only Transaction 안에서 조회와 응답 조립만 담당하고, Command는 권한과 소유권을 확인한 뒤 Aggregate의 상태 변경 메서드를 호출한다. 자식 생성과 연관관계 연결은 `Movie.addMoviePerson(...)`처럼 Aggregate Root가 책임지게 하여 서비스가 JPA 양방향 연결 절차를 알지 않아도 되게 했다.

외부 작업과 DB Transaction도 분리했다. 오케스트레이션 서비스는 Storage 저장, 파일 확인, BCrypt 같은 외부·고비용 작업의 순서를 조율하고, 별도 Transaction Service가 짧은 DB 조회·잠금·상태 변경만 담당한다. 신규 파일 저장 후 DB 반영이 실패하면 보상 삭제하고, 기존 파일은 DB Commit 이후 Event로 삭제한다. Job과 Outbox처럼 반드시 함께 저장돼야 하는 데이터만 하나의 DB Transaction으로 묶었다. 이 구조로 외부 시스템 지연 중 DB Connection과 Lock을 오래 점유하는 위험을 줄였지만, 오케스트레이터와 Transaction Service 사이의 코드와 실패 보상 정책은 추가로 관리해야 한다.

에러 처리에서는 서비스마다 `IllegalArgumentException`, 전용 예외, 문자열 메시지 비교와 HTTP 상태 직접 지정이 혼재했다. 같은 오류도 발생 위치에 따라 응답 코드와 메시지가 달랐고, 미디어 상태 오류를 예외 메시지 문자열로 구분해 문구가 변경되면 분기가 깨질 수 있었다. 인증 Filter의 401·403 응답도 Controller 예외 응답과 형식이 달랐다.

이를 해결하기 위해 `ErrorCode`를 오류 정책의 단일 기준으로 정했다. 각 ErrorCode가 안정적인 공개 코드, HTTP 상태와 사용자에게 보여줄 메시지를 소유하고, `DomainException`은 해당 ErrorCode를 전달한다. 사용자·인증·미디어·Storage 등 기존 전용 예외는 `DomainException`을 기반으로 전환해 호출부가 구체적인 도메인 의미를 유지하면서도 공통 처리 흐름을 사용하게 했다. 미디어 작업 상태도 문자열 비교 대신 타입이 지정된 ErrorCode와 예외로 구분했다.

`GlobalExceptionHandler`는 DomainException을 중심으로 응답을 생성하도록 정리했다. Bean Validation 실패, JSON 형식 오류, 지원하지 않는 요청, DB UNIQUE 위반, 낙관적 Lock 충돌과 예상하지 못한 서버 오류도 공통 API Error Response로 변환한다. 예상 가능한 도메인 오류는 정해진 코드와 공개 메시지를 반환하고, 예상하지 못한 오류는 내부 예외 정보나 Stack Trace를 Client에 노출하지 않고 공통 서버 오류로 응답한다. 상세 원인은 Server Log에 남겨 운영자가 추적할 수 있게 했다.

Spring Security에서 Controller까지 도달하지 않는 인증 실패도 같은 정책을 사용하도록 Authentication EntryPoint와 AccessDeniedHandler를 정리했다. 로그인하지 않은 요청은 401, 권한이 부족한 요청은 403으로 구분하면서 일반 API와 동일한 오류 응답 구조를 반환한다. 따라서 Client는 오류가 Controller, Security Filter, Bean Validation 또는 DB 충돌 중 어디에서 발생했는지와 관계없이 안정적인 `errorCode`를 기준으로 처리할 수 있다.

최종적으로 서비스 이름과 Transaction 경계만 봐도 조회·상태 변경·외부 작업의 책임을 구분할 수 있게 됐고, 모든 주요 실패 경로가 ErrorCode를 통해 동일한 HTTP 상태와 응답 형식을 사용하게 됐다. 대신 ErrorCode 목록과 공개 메시지를 중앙에서 관리하고 새 예외를 추가할 때 코드 중복과 계층 책임을 검토해야 한다. 이번 작업의 핵심은 단순히 클래스 수를 늘린 것이 아니라 정상 흐름의 책임과 실패 흐름의 계약을 함께 명확하게 만든 것이다.

관련 상세 문서:

- [서비스 책임을 Command와 Query로 분리](07-service-command-query-separation.md)
- [도메인 예외와 API 오류 응답 표준화](08-domain-exception-and-api-error-standardization.md)
- [DB 트랜잭션과 외부 I/O 경계 분리](09-transaction-boundary-and-external-io.md)
- [주요 API Error Response 일관성 점검](../review/error/api-error-response-consistency.md)

## 2. DB·JPA 신뢰성 개선

이번 작업의 목표는 “JPA 코드가 동작한다”를 넘어 실제 MySQL에서도 Schema, Constraint, Transaction, 동시성과 Index가 의도대로 동작한다는 근거를 만드는 것이었다.

기존에는 Hibernate의 `ddl-auto`가 실행 환경에 따라 테이블을 자동 생성하거나 변경했다. 이 방식은 편리하지만 언제 어떤 Schema가 적용됐는지 추적하기 어렵고, Entity 변경과 실제 DB 구조가 달라질 위험이 있었다. 빠른 테스트에 사용한 H2도 MySQL의 Collation, CHECK, FK, enum, InnoDB Lock과 실행 계획을 완전히 재현하지 못했다.

이를 해결하기 위해 Flyway를 Schema의 단일 기준으로 채택했다. API는 V1~V5, Worker는 V1~V2 Migration을 독립적으로 관리하며 Hibernate는 `ddl-auto: validate`로 Entity와 DB가 일치하는지만 검사한다. 아직 보존할 운영 데이터가 없었기 때문에 기존 DB를 baseline 처리하지 않고 빈 DB에서 V1부터 재현하는 정책을 선택했다. 적용된 Migration은 수정하지 않고 이후 변경은 새로운 버전으로 추가한다.

API와 Worker는 하나의 MySQL 서버를 사용하되 `onfilm_api`, `onfilm_worker`라는 논리 DB와 계정을 분리했다. 서로의 DB를 직접 조회하거나 FK와 JOIN으로 연결하지 않고 Kafka 메시지와 인증된 Callback API로만 데이터를 교환한다. 현재 규모에서는 물리 MySQL을 분리하는 운영 비용을 피하면서도 데이터 소유권과 변경 책임을 명확히 할 수 있는 선택이다.

실제 MySQL 검증을 위해 MySQL 8.4.11 Testcontainers 환경도 구축했다. CI는 H2 기반의 빠른 테스트와 MySQL 통합 테스트를 별도 작업으로 실행한다. 이 과정에서 H2에서는 발견하지 못했던 UUID의 `BINARY(36)` Mapping 문제와 Kafka JSON payload가 `TINYTEXT`로 생성되어 길이 제한을 넘는 문제를 발견했다. 각각 `VARCHAR(36)`과 `TEXT`로 명시해 실제 저장 형식을 바로잡았다.

DB Constraint도 전수 검토했다. 서비스의 사전 검증은 사용자에게 구체적인 오류를 제공하고, DB의 UNIQUE·FK·CHECK는 동시 요청과 다른 저장 경로까지 막는 최종 방어선으로 구분했다. API의 19개 테이블을 감사하고 Worker Inbox에는 횟수, 시간 순서, JSON 형식, 상태별 lease·실패 정보 조합을 보호하는 CHECK 10개를 추가했다. 반면 `@OrderColumn`은 Hibernate가 INSERT 후 순서를 UPDATE하는 중간 상태가 필요하므로 무조건 NOT NULL이나 UNIQUE로 강화하지 않았다.

동시성은 두 스레드와 독립된 MySQL Transaction으로 검증했다. 동시 회원가입은 DB UNIQUE가 하나만 성공시키고, Refresh Token과 Media Job의 동시 상태 변경은 `@Version` 낙관적 Lock으로 충돌을 탐지한다. 작업 선점처럼 즉시 직렬화가 필요한 구간은 비관적 Lock을 사용했다. Worker에서는 같은 `jobId` 메시지의 동시 최초 저장을 재현해 Primary Key 충돌과 MySQL deadlock을 처리하고, 최종적으로 하나는 처리하고 다른 하나는 중복 작업으로 정리되게 했다.

Index 역시 추측으로 추가하지 않고 20만 건의 benchmark 데이터와 `EXPLAIN ANALYZE`로 검증했다. API는 효과가 확인된 세 개만 추가해 완료 Job 정리를 123ms에서 3.40ms, Outbox 정리를 1,357ms에서 3.57ms로 개선했다. Worker는 기존 Index로 실패 Callback 조회가 129ms에서 0.753ms로 줄어드는 것을 확인했지만 추가 Index는 이득이 없어 V3 Migration을 만들지 않았다.

최종적으로 API 342개와 Worker 66개, 총 408개 테스트가 모두 통과했으며 이 중 76개가 실제 MySQL 통합 테스트다. 대신 SQL Migration 관리 비용, Docker 기반 테스트 시간, Index의 쓰기·저장 비용을 감수하게 됐다. 이번 작업의 핵심은 Flyway나 Testcontainers를 단순 도입한 것이 아니라 Schema 변경부터 동시성·성능까지 실제 MySQL에서 재현하고 설명할 수 있는 개발 흐름을 만든 것이다.

관련 상세 문서:

- [Flyway와 실제 MySQL 검증으로 DB·JPA 신뢰성 확보](11-database-jpa-reliability.md)
- [DB·JPA 신뢰성 컨벤션](../convention/database-jpa-reliability-convention.md)
- [API·Worker DB 소유권과 Flyway 정책](../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md)
- [API Index 적용 전후 비교](../performance/mysql-index-before-after-comparison.md)

## 3. 장애 대응과 관측성 개선

이번 작업의 목표는 정상 요청이 성공하는지만 확인하는 수준을 넘어 Kafka 중복 전달, Worker 종료, Callback 중복·순서 역전, 외부 시스템 Timeout과 서버 종료 상황에서도 작업을 추적하고 안전하게 복구할 수 있는 구조를 만드는 것이었다.

먼저 장애를 모두 같은 방식으로 재시도하지 않고 성격에 따라 분류했다. Kafka·S3·Callback처럼 일시적으로 복구될 가능성이 있는 오류는 제한된 횟수와 지수 Backoff를 적용하고, 잘못된 요청이나 지원하지 않는 형식 같은 영구 오류는 즉시 실패 처리한다. 영상 업로드까지 완료되고 Callback만 실패한 경우에는 비싼 인코딩을 다시 실행하지 않고 `OUTPUT_UPLOADED` 상태에서 Callback만 재시도한다. Worker 처리 자체가 실패한 경우에만 정책이 허용하는 범위에서 전체 작업을 재실행한다.

재시도 대기 중에는 Thread나 DB Connection을 잡고 `sleep`하지 않는다. 다음 실행 시각과 상태를 DB 또는 Kafka Retry Topic에 기록하고 현재 처리를 종료한다. 재시도 횟수를 초과한 Kafka 메시지는 DLT로, 발행할 수 없는 Outbox는 `DEAD`, 복구할 수 없는 인코딩 작업은 `FAILED`로 이동시킨다. 자동 재처리와 수동 재처리의 경계도 문서로 구분했다.

Kafka의 at-least-once 전달로 같은 메시지가 여러 번 도착할 수 있으므로 `jobId` 기반 Worker Inbox를 멱등성 기준으로 사용했다. 같은 메시지가 다시 전달되면 인코딩을 중복 실행하지 않으며, 처리 중 Worker가 종료되면 lease 만료 후 다른 실행이 작업을 복구한다. Callback도 현재 Job 상태와 허용 상태 전이를 확인해 `DONE` Callback 중복이나 `DONE → FAILED` 같은 역방향 변경을 거부한다.

DB에 Job을 저장한 직후 Kafka 발행 전에 서버가 종료되는 문제는 Transactional Outbox로 방어했다. Job과 발행할 메시지를 같은 DB Transaction으로 저장하고 별도 Publisher가 Commit된 Outbox를 Kafka에 발행한다. Publisher가 중간에 종료돼도 발행 의도가 DB에 남아 lease 만료 후 다시 선점할 수 있다. 발행 실패에는 최대 횟수와 지수 Backoff를 적용하며 원문 Token이나 인증 정보는 Log에 남기지 않는다.

Timeout도 한 가지 값으로 통일하지 않고 호출 목적별로 분리했다. 한 번의 HTTP·S3 요청이 응답하지 않을 때 중단하는 호출 Timeout, FFmpeg가 영상을 처리할 수 있는 실행 Timeout, Worker 소유권을 나타내는 processing lease, API가 Job을 실패로 판단하는 전체 Job Timeout을 서로 다른 개념으로 정의했다. 전체 Job Timeout은 정상 Worker 처리와 lease 복구가 끝날 시간을 고려해 Worker 제한보다 길게 설정했다.

장애가 발생했을 때 요청 흐름을 연결하기 위해 API에서 발급한 `correlationId`를 HTTP Header, Kafka Message, Worker MDC와 내부 Callback까지 전파했다. 구조화 Log에는 `correlationId`, `requestId`, `jobId`, `movieId`, `eventType`, `status`, `elapsedTime`, `errorCode`를 필요한 범위에서 기록한다. 반면 `jobId`, `userId`, `requestId` 같은 값은 Prometheus Tag로 사용하지 않았다. 요청마다 다른 값을 Tag로 만들면 시계열 수가 폭증해 Monitoring 시스템의 Memory와 저장 공간을 소모하기 때문이다.

API에는 Job 상태, Outbox backlog·발행 성공·실패·DEAD, Callback 결과와 처리 시간을 측정하는 Metric을 추가했다. Worker에는 인코딩 성공률, 단계별 소요 시간, 실패 단계와 오류 코드, 중복 메시지, stale recovery, Callback과 DLT 처리 결과를 추가했다. 상태별 Gauge는 Prometheus가 조회할 때마다 DB에 접근하지 않고 30초마다 DB Snapshot을 Memory에 갱신하도록 했다. 값이 최대 한 주기 늦을 수 있지만 Monitoring 요청이 DB 부하로 전파되는 것을 줄이는 선택이다.

Spring Actuator Metric을 Prometheus가 수집하고 Grafana Dashboard에서 API 오류율, 응답 시간, Kafka 처리, Job·Outbox·Inbox 상태와 인코딩 결과를 확인할 수 있게 했다. Outbox 적체와 `DEAD`, Job Timeout, Worker 중단, Callback 실패, HTTP 5xx 비율 등의 Alert Rule도 구성했다. Alert 기준은 아직 실제 운영 트래픽이 없는 초기값이므로 서비스 운영 후 정상 범위와 오탐을 기준으로 조정해야 한다.

장애 주입 테스트에서는 DB Commit과 Rollback, 중복 메시지, 중복·역순 Callback, lease 만료, Outbox 발행 실패, Callback-only 복구와 최종 실패 상태를 검증했다. Runbook에는 증상별로 확인할 Dashboard, Log 검색 키, DB 상태, 예상 원인, 복구 명령과 정상화 확인 순서를 기록했다.

결과적으로 장애를 단순히 Log로 남기는 수준에서 벗어나 실패 분류, 상태 저장, 재시도, 멱등성, Metric, Alert와 복구 절차가 하나의 흐름으로 연결됐다. 다만 실제 Kafka 단절, S3 장애와 운영 규모의 부하는 아직 경험하지 않았으며 Circuit Breaker와 Alert 임계값은 실제 관측 데이터가 필요하므로 후속 과제로 남겼다.

관련 상세 문서:

- [미디어 파이프라인의 장애 대응과 관측성 구축](10-media-failure-observability-and-runbook.md)
- [미디어 인코딩의 원자성과 멱등성](05-media-encode-outbox-and-worker.md)
- [미디어 장애 처리 정책](../decisions/media-failure-handling-policy.md)
- [미디어 장애 대응 Runbook](../operations/media-incident-runbook.md)
