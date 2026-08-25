# 트랜잭션 경계 감사

- 감사일: 2026-08-25
- 기준 커밋: `2e8f23a`
- 범위: Command·Query 서비스, 인증·Refresh Token, 파일 저장·삭제, 미디어 인코딩 Job·Outbox
- 상태: 리팩토링 전 감사 기록. 아래 개선 대상은 `aabe3dd`~`5808bdb`에서 완료

> 이 문서는 개선 전 위험을 식별한 당시의 스냅샷이다. 현재 적용된 경계와 설계 원칙은 [Transaction Boundary 설계 가이드](transaction-boundary-guide.md)를 기준으로 한다.

## 목적

트랜잭션은 DB 변경을 원자적으로 묶는 범위까지만 유지하는 것이 기본 원칙이다. 네트워크 파일 저장, S3 존재 확인, 영상 인코딩, Kafka 발행처럼 응답 시간이 길거나 실패 방식이 다른 작업까지 같은 범위에 포함하면 DB 커넥션과 잠금 점유 시간이 늘어난다.

이번 감사에서는 다음 항목을 확인했다.

- Command와 Query의 트랜잭션 구분
- DB 트랜잭션 내부의 스토리지·Kafka 등 외부 I/O
- `REQUIRES_NEW`와 `AFTER_COMMIT`의 프록시 적용 조건
- 예외가 발생했을 때 DB 변경과 외부 자원의 정합성
- 롤백·재시도·멱등성을 검증하는 테스트 유무

## 현재 경계 요약

| 영역 | 현재 경계 | 판정 |
|---|---|---|
| 일반 Command 서비스 | 클래스 단위 쓰기 트랜잭션 | 적절 |
| 일반 Query 서비스 | 클래스 단위 `readOnly = true` | 적절 |
| Refresh Token 보안 기록 | 별도 Bean의 `REQUIRES_NEW` | 적절 |
| 기존 파일 삭제 | `AFTER_COMMIT` 이벤트 | 적절, 호출 계약 보강 필요 |
| Job·Outbox 생성 | 동일 DB 트랜잭션 | 적절 |
| Kafka 발행 | DB 트랜잭션 밖에서 발행 후 상태를 별도 트랜잭션으로 기록 | 적절 |
| 프로필·영화 미디어 저장 | 인코딩과 스토리지 저장이 DB 트랜잭션 내부에서 실행 | 개선 필요 |
| 인코딩 요청 완료 | 업로드 요청 비관적 잠금 중 S3 존재 확인 | 우선 개선 필요 |
| Worker 완료 Callback | DB 트랜잭션 중 결과 파일 존재 확인 | 개선 필요 |

## 유지할 경계

### 1. Command와 Query 분리

`Person`, `Gallery`, `Filmography`, `Movie`, `Storyboard`의 Command 서비스는 쓰기 트랜잭션을 사용하고 Query 서비스는 `readOnly = true`를 사용한다. 조회 중 엔티티를 변경하는 경로도 발견되지 않았다.

`StorageService.toPublicUrl()`은 Local과 S3 구현 모두 문자열을 조합하는 작업이다. S3 API를 호출하지 않으므로 Query 트랜잭션 내부에서 사용해도 외부 I/O 대기 문제가 없다.

### 2. Job과 Outbox의 원자적 저장

`MediaEncodeJobCommandService`가 `MediaEncodeJob`, `MediaEncodeOutbox`, `MediaUploadRequest` 완료 상태를 하나의 트랜잭션에서 변경한다. 세 변경 중 하나라도 실패하면 모두 롤백되므로 Kafka에 전달할 수 없는 Job만 DB에 남거나, Job 없이 Outbox만 남는 상태를 방지한다.

Kafka 발행은 `MediaEncodeOutboxPublisher`가 트랜잭션 밖에서 수행한다. 발행 전 선점과 발행 성공·실패 기록은 `MediaEncodeOutboxTransactionService`의 별도 `REQUIRES_NEW` 트랜잭션으로 짧게 처리한다. Kafka 응답을 기다리는 동안 DB 트랜잭션을 유지하지 않는 구조다.

### 3. 보안 기록용 REQUIRES_NEW

`RefreshTokenSecurityTransactionService`는 `RefreshTokenService`와 별도 Bean이다. 따라서 Spring 프록시를 통과해 `REQUIRES_NEW`가 실제 적용된다.

- 만료 토큰 접근: 외부 인증 트랜잭션이 401 예외로 롤백돼도 폐기 기록 커밋
- 폐기 토큰 재사용: 사용자 전체 세션 폐기를 독립 커밋한 뒤 경고 로그와 401 반환

원문 토큰과 토큰 해시는 로그에 기록하지 않는다.

### 4. 기존 파일의 커밋 후 삭제

프로필, 갤러리, 영화, 스토리보드의 기존 파일 삭제는 `StorageFilesDeleteEventListener`가 `AFTER_COMMIT`에 수행한다. DB 변경이 롤백되면 사용 중인 파일을 먼저 삭제하는 문제를 피한다. 개별 파일 삭제 실패는 로그를 남기고 다음 파일을 계속 처리한다.

## 개선 대상

### P1. 업로드 요청 잠금 중 S3 존재 확인

`MediaEncodeJobCommandService`는 `MediaUploadRequest`를 `findByIdForUpdate()`로 잠근 뒤 `storageService.exists()`를 호출한다. S3 환경에서는 `HeadObject` 네트워크 응답을 기다리는 동안 행 잠금과 DB 트랜잭션이 유지된다.

중복 완료 요청을 막기 위한 비관적 잠금 자체는 필요하지만, 외부 I/O를 잠금 구간에 포함할 필요는 없다. 다음 구조를 권장한다.

1. 잠금 없는 조회로 요청 소유권·만료·key 정책을 1차 검증한다.
2. 트랜잭션 밖에서 원본 파일 존재를 확인한다.
3. 짧은 쓰기 트랜잭션에서 같은 요청을 잠금 조회한다.
4. 상태와 입력을 다시 검증하고 Job·Outbox·요청 완료를 함께 저장한다.

두 번째 검증은 1차 검증 이후 발생할 수 있는 동시 요청과 상태 변경을 막기 위해 생략하지 않는다.

### P1. 동기 미디어 인코딩·저장 중 DB 트랜잭션 유지

`MovieMediaService`는 편집 권한과 Movie를 조회한 뒤 영상·이미지 인코딩 및 스토리지 저장을 수행한다. ffmpeg 처리와 S3 업로드가 끝날 때까지 DB 트랜잭션과 영속성 컨텍스트가 유지된다.

`PersonMediaService`도 Person을 조회한 뒤 S3 저장이 완료될 때까지 트랜잭션을 유지한다. 파일 크기나 네트워크 지연이 커질수록 커넥션 풀 고갈 가능성이 높아진다.

서비스를 다음 두 역할로 분리한다.

- 비트랜잭션 오케스트레이터: 파일 검증, 인코딩, 신규 파일 저장, 실패 시 보상 삭제
- 트랜잭션 Command Bean: 소유권 재검증, 엔티티 변경, 기존 key 삭제 이벤트 발행

DB 변경 실패 시 신규 파일을 보상 삭제하고, 기존 파일은 DB 커밋 이후에만 삭제한다. 별도 Bean으로 분리해야 트랜잭션 self-invocation 문제를 피할 수 있다.

### P2. 완료 Callback 트랜잭션 중 S3 존재 확인

`MediaEncodeJobInternalService.complete()`는 Job을 조회하고 출력 정보를 검증한 뒤 S3 `HeadObject`를 호출한다. 비관적 잠금은 아니지만 외부 응답 동안 DB 트랜잭션을 유지한다.

Job 예상 출력 정보의 읽기 스냅샷을 먼저 얻고 트랜잭션 밖에서 파일을 확인한 다음, 짧은 쓰기 트랜잭션에서 Job 상태와 출력 정보를 재검증하고 Movie와 Job을 함께 변경하는 방식으로 분리한다. 중복 Callback의 멱등성은 마지막 쓰기 트랜잭션에서 판단한다.

### P2. 커밋 후 삭제 Publisher의 호출 계약

`@TransactionalEventListener`는 활성 트랜잭션 없이 발행된 이벤트를 기본적으로 처리하지 않는다. 현재 `StorageFileDeletionPublisher`의 모든 호출자는 쓰기 트랜잭션 안에 있지만, Publisher 자체는 이 전제를 강제하지 않아 이후 비트랜잭션 호출이 추가되면 삭제 요청이 조용히 사라질 수 있다.

Publisher가 활성 트랜잭션을 확인하고 잘못된 호출을 즉시 실패시키도록 계약을 보강한다. `fallbackExecution = true`는 롤백 안전성을 약화시키므로 사용하지 않는다.

### P3. 인증 트랜잭션 길이

`AuthService.signup()`은 BCrypt 인코딩을, `login()`은 비밀번호 비교와 Access Token 생성을 쓰기 트랜잭션 안에서 수행한다. 외부 네트워크 I/O는 아니지만 의도적으로 느린 BCrypt 연산 때문에 트랜잭션이 길어진다.

현재 규모에서는 정합성 문제가 없고 우선순위가 낮다. 미디어 경계를 먼저 개선한 후 다음을 별도 검토한다.

- 회원가입: 값 검증과 비밀번호 인코딩 후 짧은 저장 트랜잭션 실행
- 로그인: 사용자 조회와 비밀번호 비교 후 Refresh Token 발급 트랜잭션 실행
- Refresh Token 회전: 기존 토큰 소비와 신규 토큰 저장은 계속 하나의 트랜잭션으로 유지

## 의도적으로 유지하는 비트랜잭션 경계

Local raw upload는 `authorizeRawUpload()`의 짧은 DB 검증 트랜잭션이 끝난 뒤 파일을 저장한다. 큰 요청 본문 복사와 스토리지 저장 중 DB 트랜잭션을 유지하지 않는다는 점에서 적절하다.

검증 직후 요청이 만료될 수 있는 작은 시간차가 있지만 완료 API가 요청 상태·소유권·만료를 다시 검증한다. 저장된 원본이 Job으로 연결되지 않으면 별도 raw 파일 정리 정책의 대상이며, DB 트랜잭션을 업로드 전체에 걸쳐 유지하는 것보다 안전하다.

Presigned URL 생성은 S3 Presigner의 로컬 서명 계산으로 원격 API를 호출하지 않는다. `MediaUploadRequestService.issue()` 안에 있어도 네트워크 대기는 발생하지 않는다. 다만 URL 발급 후 DB 저장 실패 시 해당 URL로 업로드할 수는 있으나, 완료 API는 DB에 없는 `requestId`를 거부한다.

## 다음 구현 커밋 순서

각 항목은 동작 변경과 테스트를 함께 포함하는 최소 커밋 단위다.

1. `StorageFileDeletionPublisher`가 활성 트랜잭션을 요구하도록 계약과 테스트를 추가한다.
2. 인코딩 요청의 S3 존재 확인을 비관적 잠금 밖으로 이동하고 동시 완료·롤백 테스트를 보강한다.
3. Worker 완료 Callback의 결과 파일 확인과 DB 반영 경계를 분리하고 중복 Callback 테스트를 보강한다.
4. `PersonMediaService`를 비트랜잭션 오케스트레이터와 DB Command로 분리하고 보상 삭제·커밋 후 삭제를 검증한다.
5. `MovieMediaService`를 같은 구조로 분리하고 인코딩·S3 저장 중 트랜잭션이 없음을 검증한다.
6. 필요하면 인증의 BCrypt·JWT 연산과 DB 쓰기 경계를 별도 리팩토링한다.

## 검증 기준

구현 단계에서는 다음 조건을 자동화 테스트로 고정한다.

- DB 롤백 시 기존 파일을 삭제하지 않는다.
- DB 반영 실패 시 새로 저장한 파일을 보상 삭제한다.
- 파일 삭제 실패가 이미 커밋된 DB 변경을 되돌리지 않으며 오류 로그를 남긴다.
- S3 존재 확인 중 DB 쓰기 트랜잭션 또는 비관적 잠금을 유지하지 않는다.
- Job과 Outbox는 함께 커밋되거나 함께 롤백된다.
- 만료 토큰 사용 기록과 토큰 재사용 대응은 외부 401 롤백과 무관하게 커밋된다.
- 중복 완료 요청과 중복 Callback은 하나의 최종 상태만 만든다.

현재 관련 회귀 테스트는 다음 책임을 이미 검증한다.

- `StorageFilesDeleteEventListenerTest`: 커밋 후 삭제, 롤백 시 미삭제, 개별 실패 후 계속 처리
- `MediaEncodeOutboxPersistenceTest`: Job·Outbox 원자적 롤백
- `MediaEncodeOutboxPublisherTest`: 발행 성공·실패 상태 기록
- `RefreshTokenExpirationPersistenceTest`: 외부 롤백과 독립된 만료 토큰 폐기 기록
