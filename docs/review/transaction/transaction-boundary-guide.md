# Transaction Boundary 설계 가이드

- 기준일: 2026-08-25
- 대상: OnFilm API 서버의 Spring `@Transactional` 경계
- 관련 커밋: `aabe3dd`, `7b8cf7f`, `16bca90`, `827a860`, `e11eae9`, `5808bdb`
- 상태: 현재 구현 기준 완료

## Transaction Boundary란

Transaction Boundary는 **어떤 DB 작업들을 하나의 성공 또는 실패 단위로 묶을지 정하는 경계**다. Spring에서는 일반적으로 프록시를 통해 `@Transactional` 메서드에 진입할 때 트랜잭션을 시작하고, 메서드가 정상 반환하면 커밋하며, 롤백 대상 예외가 밖으로 전파되면 롤백한다.

트랜잭션 안에서는 다음 자원을 사용한다.

- DB connection
- JPA persistence context와 dirty checking
- 격리 수준에 따른 row/version 상태
- 비관적 잠금을 사용한다면 해당 row lock

따라서 범위가 길어질수록 connection과 lock 점유 시간도 길어진다. 트랜잭션을 넓게 잡는 것이 항상 더 안전한 것은 아니다.

## OnFilm의 핵심 원칙

> DB에서 반드시 함께 성공해야 하는 변경만 하나의 트랜잭션으로 묶고, S3·파일 시스템·ffmpeg·Kafka·BCrypt처럼 DB가 원자성을 보장할 수 없는 작업은 경계 밖에서 수행한다.

DB 트랜잭션은 다음 작업까지 롤백해 주지 않는다.

- 이미 S3에 저장하거나 삭제한 객체
- 로컬 파일 시스템에 쓴 파일
- 실행이 끝난 ffmpeg 프로세스
- 이미 Kafka broker에 전달한 메시지
- 수행이 끝난 BCrypt 연산

외부 작업까지 트랜잭션 안에 넣으면 원자성이 확장되는 것이 아니라 DB 자원을 더 오래 점유한다. 외부 자원과의 정합성은 재검증, 멱등성, 보상 작업, Outbox와 커밋 후 이벤트로 해결한다.

## 경계 유형

| 경계 | 의미 | OnFilm 사용처 |
| --- | --- | --- |
| `@Transactional(readOnly = true)` | 조회용 persistence context와 일관된 읽기 범위 | 일반 Query, 권한·상태 snapshot 조회 |
| `@Transactional` | 함께 커밋하거나 롤백할 DB 상태 변경 | Command, Aggregate 변경, Job·Outbox 저장 |
| `REQUIRES_NEW` | 기존 트랜잭션을 중단하고 독립 트랜잭션 시작 | 401과 무관하게 남겨야 하는 보안 기록, Outbox 선점·결과 기록 |
| `AFTER_COMMIT` | DB 커밋 성공 이후 부수 효과 실행 | 더 이상 참조하지 않는 기존 파일 삭제 |
| 비트랜잭션 오케스트레이터 | 외부 I/O와 여러 짧은 DB 경계의 실행 순서 조정 | Person·Movie 미디어, Job 요청, Callback 완료, signup·login |

`readOnly = true`는 조회 의도를 명확히 하고 JPA 최적화에 도움을 주지만 DB 종류와 설정에 따라 쓰기를 절대 차단하는 보안 장치는 아니다. 조회 서비스가 상태를 변경하지 않는 규칙은 코드 구조와 테스트로 함께 지킨다.

## 기본 Command와 Query

외부 I/O가 없는 일반 유스케이스는 서비스 메서드 하나가 경계가 된다.

```text
Controller
  → QueryService [readOnly transaction]
      → 조회·공개 범위 판단·DTO 조립

Controller
  → CommandService [write transaction]
      → 대상·권한 조회
      → Aggregate 상태 변경
      → commit 또는 rollback
```

- Query: `PersonQueryService`, `GalleryQueryService`, `FilmographyQueryService`, `StoryboardQueryService`, `UserQueryService`
- Command: `PersonCommandService`, `GalleryCommandService`, `FilmographyCommandService`, `MovieCommandService`, `StoryboardCommandService`

한 유스케이스에서 여러 엔티티가 함께 성공해야 하면 같은 쓰기 트랜잭션에 둔다. 예를 들어 Filmography 변경에서 Movie, MoviePerson과 MovieGenre의 상태는 하나의 결과이므로 함께 커밋한다.

## 외부 I/O가 있는 공통 패턴

외부 I/O가 끼면 다음 세 단계가 기본이다.

```text
1. 짧은 읽기 트랜잭션
   대상·소유권·현재 상태를 확인하고 ID 또는 불변 snapshot 반환

2. 트랜잭션 밖
   S3 HEAD/PUT, 파일 저장, ffmpeg, BCrypt 등 실행

3. 짧은 쓰기 트랜잭션
   대상·소유권·상태를 다시 조회하고 최종 변경을 커밋
```

1단계와 3단계 사이에는 다른 요청이 상태를 바꿀 수 있다. 따라서 첫 검증은 불필요한 외부 작업을 줄이는 빠른 실패용이고, **최종 쓰기 단계의 재검증이 정합성을 결정한다.** 이는 시간차 공격 또는 TOCTOU(Time Of Check To Time Of Use) 문제에 대응하는 구조다.

트랜잭션 밖으로 넘기는 값은 영속 Entity가 아니라 ID, storage key, enum과 불변 record snapshot을 사용한다. Entity를 넘기면 지연 로딩과 detached 상태 변경 때문에 실제 경계가 다시 불명확해진다.

## 흐름 1: Person·Movie 미디어 변경

`PersonMediaService`와 `MovieMediaService`는 비트랜잭션 오케스트레이터이고, 각 `*MediaTransactionService`가 DB 경계를 소유한다.

```text
오케스트레이터 [transaction 없음]
  → 대상 ID·편집 권한 확인 [짧은 read transaction]
  → 신규 key 생성
  → 파일 검증·ffmpeg·스토리지 저장 [transaction 없음]
  → 공개 URL 계산 [transaction 없음]
  → 권한 재검증·엔티티 key 변경 [짧은 write transaction]
      → 기존 key 삭제 이벤트 등록
  → commit
  → 기존 파일 실제 삭제 [AFTER_COMMIT]
```

실패 순서는 파일 역할에 따라 다르다.

| 대상 | 처리 정책 | 이유 |
| --- | --- | --- |
| 신규 파일 | 먼저 저장하고 DB 반영 실패 시 보상 삭제 | DB가 존재하지 않는 파일을 참조하는 상태 방지 |
| 기존 파일 | DB 참조 제거가 커밋된 뒤 삭제 | 롤백된 DB가 이미 삭제한 파일을 계속 참조하는 상태 방지 |

보상 삭제는 별도 분산 트랜잭션이 아니라 최선 노력(best effort)이다. 보상까지 실패하면 원래 실패를 유지하고 storage key와 stack trace를 로그에 남겨 후속 정리 대상으로 삼는다.

## 흐름 2: 인코딩 Job과 Outbox 생성

S3 원본 확인은 느린 네트워크 작업이고, 중복 Job 방지에는 짧은 비관적 잠금이 필요하다. 두 작업을 분리한다.

```text
MediaEncodeJobCommandService [transaction 없음]
  → 업로드 요청 1차 검증·기존 Job 확인 [read transaction]
  → source key·target key 정책 검증
  → S3 원본 존재 확인 [transaction 없음]
  → MediaEncodeJobTransactionService [write transaction]
      → MediaUploadRequest 비관적 잠금
      → 소유권·만료·상태·입력 최종 재검증
      → MediaEncodeJob 저장
      → MediaEncodeOutbox 저장
      → MediaUploadRequest 완료 처리
  → commit
```

비관적 잠금은 마지막 DB 임계 구역에서만 유지한다. S3 확인 중 경쟁 요청이 먼저 완료했더라도 잠금 후 재검증에서 기존 Job을 찾아 중복 Job과 Outbox 생성을 막는다.

Job, Outbox와 UploadRequest 완료는 하나의 DB 결과이므로 함께 커밋하거나 함께 롤백한다. Kafka 전송은 이 트랜잭션에 넣지 않는다.

## 흐름 3: Outbox 발행

```text
MediaEncodeOutboxPublisher [transaction 없음]
  → Outbox 선점 [REQUIRES_NEW]
  → Kafka send와 결과 대기 [transaction 없음]
  → PUBLISHED 또는 재시도 상태 기록 [REQUIRES_NEW]
```

Kafka 발행과 DB 상태 저장 사이에는 분산 트랜잭션이 없으므로 발행 성공 직후 프로세스가 중단되면 같은 메시지가 다시 전달될 수 있다. OnFilm은 이를 유실보다 중복을 허용하는 at-least-once 정책으로 받아들이고 Worker가 `jobId`로 멱등 처리한다.

`REQUIRES_NEW`를 사용하는 이유는 하나의 Outbox 실패가 같은 batch의 다른 항목이나 외부 호출자의 트랜잭션에 전파되지 않도록 상태 기록 단위를 분리하기 위해서다.

## 흐름 4: Worker 완료 Callback

```text
MediaEncodeJobInternalService [transaction 없음]
  → Job·Callback 출력 1차 검증, snapshot 반환 [read transaction]
  → 결과 storage key 정책과 S3 존재 확인 [transaction 없음]
  → MediaEncodeJobCompletionTransactionService [write transaction]
      → Job 상태·출력 정보 최종 재검증
      → Movie 결과 key 반영
      → Job DONE 전환
  → commit
```

Movie 반영과 Job의 `DONE` 전환은 하나의 성공 결과이므로 같은 트랜잭션에 둔다. 결과 파일 HEAD는 이 원자성에 참여하지 못하므로 밖으로 뺀다. 중복 Callback은 최종 쓰기 경계에서 이미 완료된 상태를 확인해 멱등하게 종료한다.

## 흐름 5: Refresh Token 보안 기록

Refresh Token 회전에서 기존 토큰 소비와 새 토큰 저장은 둘 중 하나만 성공하면 안 되므로 같은 트랜잭션에 둔다.

```text
RefreshTokenService.rotate [write transaction]
  → 기존 토큰 조회·검증
  → 기존 토큰 소비
  → 신규 토큰 저장
  → commit
```

반면 만료 토큰 사용과 폐기 토큰 재사용은 401을 반환하더라도 보안 기록이 사라지면 안 된다.

```text
외부 인증 흐름
  → RefreshTokenSecurityTransactionService [REQUIRES_NEW]
      → 만료 토큰 폐기 또는 사용자 전체 세션 삭제
      → 독립 commit
  → 401 예외 반환
  → 외부 transaction rollback 여부와 무관하게 보안 기록 유지
```

`REQUIRES_NEW`는 일반적인 해결책이 아니다. 실패 응답과 무관하게 반드시 보존해야 한다는 요구사항이 명확한 보안·감사 상태에만 제한해서 사용한다.

## 흐름 6: signup과 login의 BCrypt 분리

BCrypt는 보안을 위해 의도적으로 느린 CPU 연산이다. DB 원자성에 참여하지 않으므로 트랜잭션 밖에서 수행한다.

```text
signup
  → 이메일·사용자명 사전 확인 [read transaction]
  → BCrypt encode [transaction 없음]
  → 중복 최종 확인·User와 Person 저장 [write transaction]

login
  → userId·encodedPassword snapshot 조회 [read transaction]
  → BCrypt matches·Access Token 생성 [transaction 없음]
  → Refresh Token 발급 [write transaction]
```

회원가입은 BCrypt 중 다른 요청이 같은 계정 정보를 저장할 수 있으므로 마지막 트랜잭션에서 중복을 다시 확인한다. 최종 방어선은 DB unique constraint이며, 충돌은 도메인 중복 예외로 변환한다.

## Spring 프록시와 self-invocation

Spring의 기본 선언적 트랜잭션은 proxy를 통과할 때 적용된다. 같은 Bean 안에서 `this.transactionalMethod()`처럼 호출하면 프록시를 우회하므로 새 annotation의 경계가 만들어지지 않는다.

```java
// 같은 Bean 내부 호출: 의도한 별도 경계가 적용되지 않을 수 있다.
public void orchestrate() {
    persist();
}

@Transactional
public void persist() {
}
```

OnFilm은 외부 I/O 오케스트레이터와 `*TransactionService`를 별도 Bean으로 나눠 실제 프록시 호출이 일어나게 한다. `REQUIRES_NEW`를 사용하는 Refresh Token 보안 기록과 Outbox 상태 기록도 별도 Bean에 둔다.

## 예외와 rollback 규칙

- `DomainException`은 `RuntimeException`이므로 기본적으로 현재 트랜잭션을 롤백한다.
- 예외를 잡고 정상 반환하면 Spring은 성공으로 판단해 커밋할 수 있다.
- checked exception은 기본 설정에서 자동 롤백 대상이 아닐 수 있으므로 필요한 경우 정책을 명시한다.
- 외부 I/O 실패는 DB rollback만으로 복구되지 않으므로 보상 또는 재시도 정책이 필요하다.
- `AFTER_COMMIT` listener 실패는 이미 커밋된 DB를 되돌릴 수 없다. 로그와 후속 정리 책임이 필요하다.

OnFilm의 `StorageFileDeletionPublisher`는 삭제 대상이 있는데 활성 트랜잭션이 없으면 즉시 실패한다. `@TransactionalEventListener`가 트랜잭션 밖의 이벤트를 조용히 무시하는 상황을 막고 “기존 파일은 DB 커밋 후 삭제한다”는 계약을 강제한다.

## 실패 시나리오별 결과

| 실패 지점 | DB 결과 | 외부 자원 결과 | 대응 |
| --- | --- | --- | --- |
| 신규 파일 저장 실패 | DB 변경 시작 전 | 신규 파일 없음 또는 저장 실패 | 요청 실패 |
| 신규 파일 저장 후 DB 반영 실패 | DB 롤백 | 신규 파일이 남을 수 있음 | 신규 key 보상 삭제 |
| 기존 파일 삭제 전 DB 롤백 | 기존 참조 유지 | 기존 파일 유지 | `AFTER_COMMIT`이 실행되지 않음 |
| DB 커밋 후 기존 파일 삭제 실패 | 새 참조 커밋 | 기존 파일 잔존 | 오류 로그 후 후속 정리 |
| S3 HEAD 중 경쟁 요청 완료 | 아직 최종 DB 변경 없음 | 영향 없음 | 잠금 후 상태 재검증, 기존 Job 반환 또는 멱등 종료 |
| Job 저장 중 Outbox 저장 실패 | Job·Outbox·UploadRequest 모두 롤백 | Kafka 미발행 | 동일 트랜잭션 원자성 |
| Kafka 발행 후 상태 기록 전 장애 | Outbox가 재발행 가능 상태 | 메시지 중복 가능 | at-least-once와 Worker 멱등성 |
| 만료 토큰 상태 기록 후 401 | 외부 흐름은 롤백 가능 | 해당 없음 | `REQUIRES_NEW` 기록은 유지 |

## 피해야 할 경계

- Controller 전체에 `@Transactional`을 적용해 응답 직렬화까지 persistence context 유지
- 비관적 잠금을 잡은 상태에서 S3, Kafka 또는 ffmpeg 호출
- “안전해 보인다”는 이유로 유스케이스 전체를 하나의 트랜잭션으로 감싸기
- 트랜잭션 밖으로 LAZY Entity를 반환해 뒤에서 컬렉션 순회
- 같은 Bean 내부 호출만으로 `REQUIRES_NEW`나 별도 쓰기 경계가 적용된다고 가정
- 외부 파일을 먼저 삭제하고 DB 참조를 나중에 변경
- Kafka 발행과 DB 저장이 원자적이라고 가정
- 예외를 삼킨 뒤 rollback 여부를 확인하지 않기

## 테스트 방법

단순히 `@Transactional` annotation 존재만 검사하지 않고 실제 proxy를 거친 실행 상태를 검증한다.

```java
assertThat(
        TransactionSynchronizationManager.isActualTransactionActive()
).isFalse(); // 외부 I/O 호출 시점
```

현재 경계 테스트는 다음을 담당한다.

| 테스트 | 검증 내용 |
| --- | --- |
| `PersonMediaTransactionBoundaryTest` | Person 파일 저장·URL 변환은 트랜잭션 밖, 최종 DB 반영은 안 |
| `MovieMediaTransactionBoundaryTest` | ffmpeg·스토리지 작업은 트랜잭션 밖, 권한 확인과 최종 변경은 안 |
| `MediaEncodeJobTransactionBoundaryTest` | S3 원본 확인은 트랜잭션 밖, 최종 잠금·Job 생성은 안 |
| `MediaEncodeJobCompletionTransactionBoundaryTest` | 결과 파일 확인은 트랜잭션 밖, Movie·Job 반영은 안 |
| `AuthTransactionBoundaryTest` | BCrypt와 JWT 생성은 밖, User 조회·저장과 Refresh Token 발급은 안 |
| `StorageFilesDeleteEventListenerTest` | commit 후 삭제, rollback 시 미삭제, 개별 삭제 실패 격리 |
| `MediaEncodeOutboxPersistenceTest` | Job과 Outbox가 함께 commit 또는 rollback |
| `RefreshTokenExpirationPersistenceTest` | 외부 rollback과 무관한 만료 접근 기록 보존 |
| `MySqlTransactionBoundaryIntegrationTest` | 실제 MySQL의 원자적 commit·rollback과 `REQUIRES_NEW` 독립 commit |
| `MySqlPessimisticLockIntegrationTest` | UploadRequest 잠금 대기와 Outbox 중복 선점 방지 |

테스트에서는 정상 경계뿐 아니라 DB 반영 실패 시 신규 파일 보상 삭제, 중복 완료 요청, 중복 Callback, rollback 시 기존 파일 미삭제도 함께 확인한다.

## 새 유스케이스 체크리스트

- 함께 성공해야 하는 DB 변경은 무엇인가?
- 외부 I/O 또는 느린 CPU 연산이 트랜잭션 안에 들어가 있지 않은가?
- 비관적 잠금 구간은 DB 임계 구역으로만 제한했는가?
- 읽기와 최종 쓰기 사이 상태 변화에 대비해 재검증하는가?
- 경계 사이에는 Entity 대신 ID나 불변 snapshot을 전달하는가?
- 신규 외부 자원 생성 후 DB 실패에 대한 보상 정책이 있는가?
- 기존 외부 자원 삭제는 DB commit 이후인가?
- DB와 메시지 발행의 유실 공백에는 Outbox가 필요한가?
- `REQUIRES_NEW`가 정말 외부 실패와 독립 커밋되어야 하는 상태에만 적용됐는가?
- 별도 Bean 호출로 Spring proxy를 실제 통과하는가?
- rollback·중복·외부 장애 경계를 자동화 테스트로 고정했는가?

## 면접 설명 예시

OnFilm에서는 트랜잭션을 서비스 메서드 전체가 아니라 DB에서 반드시 함께 성공해야 하는 변경 단위로 잡았습니다. S3와 ffmpeg, Kafka, BCrypt는 DB 트랜잭션에 참여하지 않으므로 비트랜잭션 오케스트레이터에서 실행하고, 앞뒤의 짧은 읽기·쓰기 트랜잭션에서 권한과 상태를 재검증합니다.

파일은 신규 저장 실패에는 보상 삭제를, 기존 파일에는 `AFTER_COMMIT` 삭제를 적용했습니다. Job과 Outbox는 한 트랜잭션에 저장하되 Kafka 발행은 밖으로 분리했고, 401 뒤에도 남아야 하는 Refresh Token 보안 기록만 제한적으로 `REQUIRES_NEW`를 사용했습니다. 실제 외부 I/O 호출 시 트랜잭션 활성 여부와 rollback·중복 상황을 경계 테스트로 검증했습니다.

## 관련 문서

- [트랜잭션 경계 감사](transaction-boundary-audit.md): 리팩토링 전 위험 구간을 식별한 기록
- [DB 트랜잭션과 외부 I/O 경계 분리](../../problem-solving/09-transaction-boundary-and-external-io.md): 문제 해결 과정과 기술 선택의 트레이드오프
- [Service 단일 책임 지도](../service/service-responsibility-map.md): 오케스트레이터와 Transaction Service의 책임
- [미디어 Job Outbox 정책](../../decisions/media-encode-job-outbox-policy.md): DB 저장과 Kafka 발행의 정합성 정책
- [Refresh Token 재사용 대응 정책](../../decisions/refresh-token-reuse-policy.md): 독립 보안 트랜잭션 사용 이유
- [MySQL 트랜잭션과 잠금 통합 테스트](../../testing/mysql-transaction-and-locking.md): 두 DB 트랜잭션의 실제 잠금 순서와 검증 범위

## 유지 규칙

외부 I/O 위치, propagation, 잠금 또는 커밋 후 이벤트 정책을 변경하면 해당 경계 테스트와 이 문서를 같은 커밋에서 수정한다.
