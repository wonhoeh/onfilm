# 미디어 인코딩의 원자성과 멱등성

- 작업일: 2026-08-21
- 문서 작성일: 2026-08-24
- 관련 커밋: `04eff88`
- 상태: 완료. MediaEncode 서비스의 추가적인 단순 분리는 현재 보류

## 문제

인코딩 Job을 DB에 저장한 뒤 Kafka 메시지를 직접 발행하면 두 시스템 사이에 원자성 공백이 생긴다. DB 커밋 후 발행에 실패하면 Job은 있지만 Worker가 알지 못하고, Kafka 발행 후 DB 트랜잭션이 롤백되면 Worker는 존재하지 않는 Job을 처리한다.

네트워크 재시도 때문에 같은 완료 요청과 Kafka 메시지가 중복될 수 있으며, Worker 콜백을 신뢰할 인증과 결과가 원래 Job의 요청과 일치하는지 확인할 연결 정보도 필요했다.

## 원인

DB와 Kafka를 하나의 원자적 저장소처럼 취급했고, 메시지가 정확히 한 번만 전달된다고 가정했다. presign, 업로드 완료, Job, Worker 콜백을 연결하는 멱등성 키와 허용 상태 전이도 충분히 모델링되지 않았다.

## 해결

- presign 단계에서 `requestId`를 발급하고 `MediaUploadRequest` 저장
- 완료 요청은 동일한 requestId, sourceKey, contentType을 제출
- 업로드 요청 행 잠금과 UNIQUE 제약으로 Job을 한 번만 생성
- `MediaEncodeJob`과 `MediaEncodeOutbox`를 같은 DB 트랜잭션에 저장
- 요청 트랜잭션에서는 Kafka를 호출하지 않고 별도 Publisher가 커밋된 Outbox 발행
- Outbox에 PENDING → PUBLISHING → PUBLISHED/DEAD 상태, lease, 최대 8회 지수 백오프 적용
- Kafka acknowledgement 이후에만 PUBLISHED 처리
- Worker는 `jobId`를 멱등성 키로 사용하고 중복 메시지의 인코딩 반복 방지
- Job 상태 전이를 엔티티 상태 머신으로 제한하고 중복 콜백은 no-op 처리
- 완료 콜백의 bucket, key, content type을 Job의 예상 결과와 대조하고 실제 파일 존재 확인
- Movie 결과 반영과 Job 완료 전이를 한 트랜잭션으로 처리
- 내부 콜백에 timestamp, nonce, body hash 기반 HMAC-SHA256 인증 적용
- 오래 멈춘 Job timeout과 Job·Outbox·업로드 요청 보존 정책 적용

## 기술 선택과 트레이드오프

### Transactional Outbox

DB에 Job과 “발행해야 할 요청”을 함께 커밋하여 프로세스가 종료되어도 메시지 발행 의도를 잃지 않는다. Kafka와 DB의 분산 트랜잭션을 도입하지 않으면서 유실을 방지할 수 있다. 대신 Outbox 테이블, polling 지연, lease, 재시도와 DEAD 상태를 운영해야 한다.

### At-least-once와 Worker 멱등성

Kafka 발행 성공 직후 PUBLISHED 저장 전에 서버가 종료되면 같은 메시지가 다시 발행될 수 있다. exactly-once를 가정하지 않고 중복을 정상 상황으로 받아들여 Worker가 jobId를 처리 완료 키로 사용하도록 했다. 구현 부담이 Consumer로 이동하지만 장애 경계를 현실적으로 다룰 수 있다.

### polling Publisher

현재 규모에서는 구현과 운영이 단순한 스케줄 polling을 선택했다. 즉시성에 작은 지연이 생기고 DB 조회 부하가 추가된다. 처리량이 커지면 CDC 기반 Outbox relay를 검토한다.

### HMAC과 nonce

공유 비밀로 Worker의 요청 출처와 본문 무결성을 확인할 수 있지만 키 배포와 회전이 필요하다. nonce 저장소는 현재 메모리 기반이라 다중 인스턴스 전역 재사용 방지는 제공하지 않는다. 확장 시 Redis `SET NX EX` 또는 DB UNIQUE 제약으로 교체해야 하며 HTTPS도 함께 사용해야 한다.

## 검증

- Job 생성과 Outbox 저장의 단일 트랜잭션 롤백 테스트
- 같은 requestId 완료 요청의 멱등성·동시성 테스트
- Outbox 선점, lease 회수, 지수 백오프, DEAD 전환 테스트
- Kafka 비동기 성공·실패 결과에 따른 상태 테스트
- Worker 중복 메시지와 중복 완료 콜백 테스트
- 허용/거부 상태 전이와 timeout 테스트
- 결과 key 소유권 및 파일 존재 검증 테스트
- HMAC 서명, 시간 오차, nonce 재사용 거부 테스트

## 결과

DB와 Kafka 사이의 요청 유실 문제를 Outbox로 해결하고, 중복 전달은 Worker 멱등성으로 흡수했다. 업로드 요청부터 Job, 발행, Worker 처리, 결과 콜백까지 식별자와 상태 전이가 연결되어 장애 지점을 추적하고 복구할 수 있게 되었다.

## 포트폴리오 요약 후보

DB 저장과 Kafka 발행 사이의 원자성 문제를 Transactional Outbox로 해결하고 lease·지수 백오프를 적용한 재시도 Publisher를 구현했습니다. at-least-once 전달을 전제로 Worker와 콜백을 jobId 기반으로 멱등화하고 HMAC·nonce로 내부 콜백 위변조와 재사용을 방어했습니다.
