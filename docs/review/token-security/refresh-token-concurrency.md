# Refresh Token 동시성 방어 — 낙관적 락 + 탈취 감지

## 문제 배경

Refresh token rotation은 "한 번 쓴 refresh token은 즉시 revoke하고 새 토큰을 발급"하는 방식이다.
이 구조에서 두 가지 동시성 위협이 존재한다.

---

## 1. 동시 요청 문제 (Race Condition)

### 문제 상황

```
탭 A, B가 동시에 같은 refresh_token_X로 rotate() 호출

탭 A: findByTokenHash(hash) → 유효한 토큰 조회 (revokedAt = null)
탭 B: findByTokenHash(hash) → 유효한 토큰 조회 (A가 아직 커밋 전)

탭 A: revoke() → saveAndFlush() → 성공 → refresh_token_A 발급
탭 B: revoke() → saveAndFlush() → 같은 row를 동시에 수정 → ??
```

A, B 모두 통과하면 한 유저에게 refresh token이 2개 발급된다.

### 낙관적 락을 선택한 이유

락은 충돌 빈도에 따라 선택한다.

- **낙관적 락**: 충돌이 드물다고 가정. 일단 진행하고 커밋 시점에 충돌 감지. 충돌 시 예외 처리.
- **비관적 락**: 충돌이 잦다고 가정. 읽는 시점에 미리 락을 걸어 다른 트랜잭션을 대기시킴 (`SELECT FOR UPDATE`).

refresh token은 "같은 토큰을 동시에 두 명이 쓰는 경우"가 극히 드물기 때문에 낙관적 락이 적합하다.
비관적 락을 걸면 매 요청마다 DB row에 락이 걸려 불필요한 대기가 생긴다.

### 해결: 낙관적 락 (`@Version`)

`RefreshToken` 엔티티에 `@Version` 컬럼 추가.

```java
@Version
private Long version;
```

#### version의 의미

version은 "이 row가 몇 번 수정됐는지"를 세는 숫자다.
숫자 자체가 중요한 게 아니라 **"내가 읽은 이후에 변경이 있었냐"를 감지하는 수단**이다.

```
내가 읽었을 때 version = 0
커밋 시점에 DB version = 0  →  아무도 안 건드렸다 → 안전하게 수정 가능
커밋 시점에 DB version = 1  →  누군가 건드렸다   → 충돌, 내 수정 취소
```

결국 version은 "내가 읽은 스냅샷이 아직 유효한가"를 확인하는 도장이다.

#### 동작 방식

JPA가 UPDATE 시 `WHERE id = ? AND version = ?` 조건을 자동으로 추가한다.

```
탭 A: 조회 (version=0) → revoke → saveAndFlush → UPDATE ... WHERE version=0 → version=1로 갱신 → 성공
탭 B: 조회 (version=0) → revoke → saveAndFlush → UPDATE ... WHERE version=0 → 이미 version=1 → 0 rows updated
      → OptimisticLockException 발생 → 401 반환
```

```java
existing.revoke();
try {
    refreshTokenRepository.saveAndFlush(existing); // 즉시 flush로 충돌 조기 감지
} catch (OptimisticLockException e) {
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Concurrent refresh token rotation detected");
}
```

`saveAndFlush`를 사용하는 이유: `save`만 사용하면 트랜잭션 커밋 시점에 flush되어
예외가 컨트롤러까지 전파되어 처리가 복잡해진다.
`saveAndFlush`로 즉시 DB에 반영해 충돌을 rotate() 내부에서 바로 잡는다.

---

## 2. 탈취 감지 (Token Reuse Detection)

### 문제 상황

```
정상 흐름:
유저 → refresh_token_X 사용 → revoke → refresh_token_Y 발급

탈취 시나리오:
공격자가 refresh_token_X를 탈취
→ 유저: refresh_token_X → rotate → refresh_token_Y (정상 사용)
→ 공격자: 이미 revoked된 refresh_token_X로 재요청
```

기존 코드는 revoked 토큰 재요청을 단순 401로 처리했다.
이미 revoked된 토큰이 다시 들어왔다는 건 정상 유저 또는 공격자 중 하나가
탈취된 토큰을 사용하고 있다는 신호다.

### 해결: 전체 토큰 revoke

revoked 토큰 재사용이 감지되면 해당 유저의 **모든 refresh token을 삭제**해
모든 기기에서 강제 로그아웃시킨다.

```java
// revokedAt 무관하게 토큰 조회 (findByTokenHashAndRevokedAtIsNull → findByTokenHash)
RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
        .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));

// 이미 revoked → 탈취 의심 → 해당 유저 전체 토큰 삭제
if (existing.isRevoked()) {
    refreshTokenRepository.deleteAllByUserId(existing.getUserId());
    throw new ResponseStatusException(UNAUTHORIZED, "Token reuse detected");
}
```

```java
// Repository
void deleteAllByUserId(Long userId);
```

---

## 최종 rotate() 흐름

```
1. tokenHash로 토큰 조회 (revoked 포함)
   → 없으면: 처음부터 없는 토큰 → 401

2. isRevoked() 확인
   → revoked면: 탈취 의심 → 해당 유저 전체 토큰 삭제 → 401

3. isExpired() 확인
   → 만료면: revoke 후 → 401

4. revoke() 후 saveAndFlush()
   → OptimisticLockException: 동시 요청 충돌 → 401

5. 새 토큰 발급 → 200
```

---

## 각 방어의 역할 비교

| 위협 | 방어 수단 | 결과 |
|---|---|---|
| 정상 유저 동시 탭 요청 | 낙관적 락 (@Version) | 첫 번째만 성공, 두 번째 401 |
| 공격자가 탈취 토큰 재사용 | Token Reuse Detection | 전체 토큰 삭제 + 401 |
| 만료된 토큰 사용 | isExpired() 체크 | revoke 후 401 |
| 존재하지 않는 토큰 | findByTokenHash() empty | 401 |
