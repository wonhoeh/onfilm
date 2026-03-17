# 트랜잭션과 락 전략 가이드

이 문서는 `onfilm` 프로젝트에 적용할 수 있는 트랜잭션, ACID, 격리 수준, InnoDB, 락 전략을 정리한 문서다.

## 면접 30초 답변 버전

“이 프로젝트는 아직 락을 많이 쓰는 구조는 아니지만, 어디에 어떤 전략이 맞는지는 구분하고 있습니다. 회원가입은 락보다 DB 유니크 제약을 최종 방어선으로 두는 게 맞고, refresh token 재발급처럼 같은 토큰이 동시에 성공하면 안 되는 곳은 비관적 락이 적합합니다. 반면 갤러리 정렬이나 스토리보드 reorder처럼 충돌 빈도는 낮지만 유실 업데이트가 문제인 영역은 낙관적 락이 더 잘 맞고, 여러 row를 한 자원처럼 직렬화해야 하면 person 단위 네임드락도 후보가 될 수 있습니다.”

## 1. 기본 관점

## 1-1. ACID

- `Atomicity`: 한 요청 안의 상태 변경은 전부 성공하거나 전부 실패해야 한다.
- `Consistency`: unique constraint, FK, 도메인 규칙이 깨지면 안 된다.
- `Isolation`: 동시에 실행되는 요청끼리 잘못된 중간 상태를 보면 안 된다.
- `Durability`: 커밋된 데이터는 장애 후에도 유지되어야 한다.

## 1-2. InnoDB와 격리 수준

실무 기준으로 MySQL `InnoDB`를 쓴다고 가정하면:

- row-level lock 지원
- MVCC 지원
- transaction 지원
- 기본 격리 수준은 보통 `REPEATABLE READ`

이 프로젝트에서는 무조건 높은 격리 수준보다 아래가 더 중요하다.

- 트랜잭션 범위를 짧게 유지
- 외부 I/O를 트랜잭션 밖으로 분리
- 필요한 곳에만 락 적용

## 2. 현재 코드 기준으로 먼저 개선할 곳

## 2-1. 회원가입

현재 구조:

- `existsByEmail`, `existsByUsername` 확인 후 저장
- DB에는 `email`, `username` unique constraint 존재

문제:

- 애플리케이션 레벨 사전 체크는 동시 요청 경쟁을 막지 못함

추천 전략:

- 락보다 DB unique constraint 중심
- 사전 중복 체크는 UX용
- 실제 저장 시 `DataIntegrityViolationException`을 `409`로 변환

면접 답변:

“회원가입은 락보다 DB 유니크 제약을 최종 방어선으로 두는 게 더 낫다고 봤습니다. 사전 체크는 사용자 경험용이고, 동시성 안정성은 DB가 보장하게 하는 구조가 더 단순하고 강합니다.”

## 2-2. Refresh Token 재발급

현재 구조:

- `findByTokenHashAndRevokedAtIsNull`로 조회
- 기존 토큰 `markUsed`, `revoke`
- 새 토큰 발급

문제:

- 같은 refresh token으로 동시에 요청이 오면 중복 발급 가능성 존재

추천 전략:

- 1순위: `PESSIMISTIC_WRITE`
- 2순위: `@Version` 기반 낙관적 락

왜 비관적 락이 우선인가:

- 같은 토큰에 대한 경쟁은 충돌 가능성이 높음
- “반드시 하나만 성공”해야 하는 케이스

## 3. Refresh Token 비관적 락 적용 설계안

이 문서는 요청한 2번 항목에 대한 구체 설계안이다.

## 3-1. 목표

- 동일 refresh token 동시 재발급 시 한 요청만 성공
- 중복 새 토큰 발급 방지
- revoke 상태 일관성 보장

## 3-2. 설계 원칙

- 토큰 조회 시 row lock 확보
- 기존 토큰 상태 변경과 새 토큰 발급을 한 트랜잭션 안에서 처리
- `token_hash`는 unique 보장

## 3-3. 필요한 변경

### 엔티티/DB

`refresh_tokens.token_hash`에 unique constraint 또는 unique index 추가 권장

이유:

- 같은 해시 토큰 중복 저장 방지
- 락 조회 대상의 일관성 확보

### Repository

비관적 락 조회 메서드 추가

예시:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select rt
    from RefreshToken rt
    where rt.tokenHash = :tokenHash
      and rt.revokedAt is null
""")
Optional<RefreshToken> findActiveByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
```

### Service

현재 `rotate()`에서 사용하는 조회 메서드를 락 메서드로 변경

흐름:

1. `tokenHash` 계산
2. `findActiveByTokenHashForUpdate()` 호출
3. row lock 확보
4. `expired/revoked` 여부 확인
5. 기존 토큰 revoke
6. 새 토큰 발급 및 저장
7. commit

## 3-4. 예상 효과

- 같은 토큰에 대한 동시 재발급 경쟁 시 한 요청만 먼저 row를 잠금
- 뒤 요청은 앞 요청 commit 후 revoke 상태를 보고 실패
- 중복 토큰 발급 가능성 감소

## 3-5. 보완 포인트

- 락 대기 시간이 길어지지 않도록 트랜잭션을 짧게 유지
- 외부 API 호출 없이 DB 작업만 수행
- 실패 시 `401` 또는 `409` 정책을 일관되게 정의

## 3-6. k6 검증 시나리오

- 동일 refresh token으로 10~50개 동시 요청
- 기대 결과:
  - 성공은 1건 또는 정책상 허용된 건수만
  - 나머지는 invalid/revoked 처리
  - 새 refresh token 중복 발급 없음

## 4. reorder / upsert 계열

대상:

- gallery reorder
- storyboard reorder
- filmography upsert

문제:

- 같은 aggregate를 동시에 수정하면 lost update 가능성 존재

추천 전략:

- 1순위: `@Version` 기반 낙관적 락
- 충돌이 실제로 많으면 비관적 락 또는 네임드락 검토

왜 낙관적 락이 맞는가:

- 보통 충돌 빈도가 높지 않음
- 충돌 시 재시도 UX가 자연스러움

추천 적용 위치:

- `Person`
- `StoryboardProject`
- 필요 시 filmography 전용 aggregate

## 5. 네임드락이 유효한 후보

`filmography upsert`는 여러 row를 한 번에 교체하는 구조라 person 단위 직렬화가 필요할 수 있다.

후보:

- `GET_LOCK('filmography:{personId}', timeout)`

적합한 상황:

- 같은 사람의 필모그래피 전체를 하나의 자원처럼 다뤄야 할 때
- 동시 수정 충돌 빈도가 높고 낙관적 락 재시도가 UX상 불리할 때

주의:

- DB 종속성이 강해짐
- 운영 복잡도 증가

실무적 권장 순서:

- 먼저 낙관적 락
- 실제 충돌이 많으면 네임드락 검토

## 6. 미디어 잡 상태 업데이트

현재는 `REQUESTED` 저장만 있고 상태 전이는 미구현이다.

추천 전략:

- 비관적 락보다 조건부 update 우선

예시:

```sql
update media_encode_job
set status = 'PROCESSING', started_at = now()
where id = ?
  and status = 'REQUESTED'
```

이유:

- 상태 전이 원자성 확보
- 중복 consume 방지
- 분산 컨슈머 환경에서 단순하고 효과적

## 7. 외부 I/O와 트랜잭션 경계

현재 일부 메서드는 트랜잭션 안에서 `storageService.delete(...)`를 호출한다.

위험:

- S3 지연이 DB 락 시간을 늘림
- 롤백 시 외부 스토리지 상태와 DB 상태 불일치 가능

추천 전략:

- DB 상태 변경은 트랜잭션 안
- 파일 삭제는 `afterCommit` 이벤트 또는 비동기 후처리

## 8. 적용 우선순위

1. 회원가입 unique violation 예외 처리 보강
2. refresh token 비관적 락 적용
3. reorder / upsert에 `@Version` 도입
4. 외부 스토리지 I/O 트랜잭션 밖으로 분리
5. media job 상태 전이 조건부 update 도입
6. 필요 시 person 단위 네임드락 검토
