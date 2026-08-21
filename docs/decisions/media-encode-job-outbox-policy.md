# Media encode job과 Transactional Outbox 정책

## 결정

미디어 업로드와 비동기 인코딩 요청은 다음 식별자를 분리한다.

- requestId: presign 발급부터 업로드 완료 요청까지의 멱등성 키
- jobId: Worker가 수행하는 하나의 인코딩 작업 식별자
- outboxId: Kafka 발행 상태와 재시도를 추적하는 식별자

presign 시 MediaUploadRequest를 저장하고 응답에 requestId를 반환한다. 완료 요청은 같은
requestId, sourceKey, contentType을 전달해야 한다. 서버는 업로드 요청을 행 잠금으로 조회하고
동일 요청으로 MediaEncodeJob을 한 번만 생성한다.

MediaEncodeJob과 MediaEncodeOutbox는 같은 DB 트랜잭션에서 저장한다. 요청 트랜잭션에서는
Kafka를 호출하지 않는다. 별도 Outbox Publisher가 커밋된 발행 요청을 선점한 후 Kafka에 발행한다.

## 전달 보장

이 구조가 제공하는 보장은 at-least-once다. 다음 장애 구간 때문에 동일한 jobId가 Kafka에
두 번 전달될 수 있다.

1. Kafka 발행 성공
2. 프로세스 종료 또는 DB 장애
3. Outbox를 PUBLISHED로 변경하지 못함
4. lease 만료 후 다시 발행

따라서 Worker는 Kafka 메시지 키인 jobId를 처리 완료 저장소의 유니크 키로 사용해야 한다.
이미 처리한 jobId를 다시 받으면 인코딩이나 결과 반영을 반복하지 않고 기존 결과로 완료
콜백을 재전송해야 한다. Worker는 이 저장소와 별도 배포 단위이므로 실제 소비자 저장 로직은
Worker 저장소에서 구현한다.

## Outbox 재시도와 동시성

- 상태: PENDING → PUBLISHING → PUBLISHED
- 최대 8회까지 지수 백오프로 재시도하며 이후 DEAD로 전환한다.
- 선점 시 2분 lease를 기록한다.
- 여러 서버는 비관적 행 잠금으로 선점하며, 종료된 서버의 레코드는 lease 만료 후 회수한다.
- Kafka acknowledgement를 받은 뒤에만 PUBLISHED로 변경한다.
- 발행 완료 Outbox는 기본 7일 보관 후 삭제한다.
- 완료 또는 실패한 Job은 기본 30일 보관하고, 만료된 업로드 요청도 정기 삭제한다.
- DEAD 레코드는 자동 삭제하지 않고 운영 확인 대상으로 남긴다.

## Job 상태와 콜백

허용 상태 전이는 다음과 같다.

- REQUESTED → PROCESSING, DONE 또는 FAILED
- PROCESSING → DONE 또는 FAILED
- 같은 상태의 중복 콜백은 첫 타임스탬프와 실패 정보를 유지하는 no-op
- DONE과 FAILED 사이의 변경은 거부

Worker가 시작 콜백을 보내지 못할 수 있으므로 REQUESTED → DONE을 허용한다. 완료 콜백은
반드시 jobId를 사용하고 Job에 저장된 예상 bucket, key, content type과 정확히 일치해야 한다.
서버는 출력 객체의 존재를 확인한 후 Movie 결과 반영과 Job의 DONE 전이를 한 트랜잭션에서
수행한다.

클라이언트 상태 조회에는 내부 failureReason을 노출하지 않고 안정적인 failureCode만 노출한다.
REQUESTED 또는 PROCESSING 상태가 기본 2시간을 초과하면 ENCODE_TIMEOUT으로 종료한다.

## 내부 콜백 인증

/internal/api/**는 공개 API가 아니다. Worker는 다음 헤더를 전송해야 한다.

- X-Onfilm-Timestamp: UTC epoch seconds
- X-Onfilm-Nonce: 요청마다 새로운 UUID
- X-Onfilm-Signature: canonical request의 HMAC-SHA256 소문자 hex

canonical request는 timestamp, nonce, HTTP method, request path,
SHA-256(request body) hex를 각각 줄바꿈 문자로 연결한다.

서버는 5분을 초과한 요청과 재사용한 nonce를 거부한다. 비밀키는
MEDIA_ENCODE_CALLBACK_SECRET으로 주입하고 로그나 저장소에 기록하지 않는다.

현재 nonce 저장소는 프로세스 메모리이므로 단일 인스턴스 또는 sticky routing을 전제로 한다.
다중 인스턴스에서 전역 replay 방지가 필요해지면 Redis SET NX EX 또는 nonce DB 유니크 제약으로
교체한다. HMAC은 Worker와 서버 사이의 인증·무결성을 제공하지만 전송 구간 보호를 대신하지 않으므로
운영 환경에서는 HTTPS를 함께 사용한다.

## 트레이드오프

장점은 DB 커밋 후 서버가 종료되더라도 발행 요청이 유실되지 않고 장애를 재시도할 수 있다는 점이다.
비용은 Outbox 테이블, polling 지연, 선점과 재시도 상태, 중복 소비 방어를 운영해야 한다는 점이다.
현재 구현은 단순한 스케줄 기반 polling을 선택했으며 처리량이 커지면 CDC 기반 Outbox relay를
검토한다.
