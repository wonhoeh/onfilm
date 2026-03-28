# JWT 기본 개념 / 커스텀 클레임 / 검증 로직 확장 정리

## 1. JWT 기본 개념

JWT(JSON Web Token)는 서버가 사용자 정보를 일정한 형식으로 담아 서명한 토큰이다.  
클라이언트는 이 토큰을 요청마다 보내고, 서버는 토큰의 서명과 만료 시간을 검증해서 사용자를 식별한다.

JWT는 보통 3부분으로 구성된다.

```text
header.payload.signature
```

- `header`
  - 어떤 알고리즘으로 서명했는지 같은 메타정보
- `payload`
  - 실제 데이터
- `signature`
  - 위 두 부분이 위조되지 않았는지 검증하는 서명값

---

## 2. 클레임(Claim)이란?

클레임은 JWT payload 안에 들어가는 개별 데이터 항목이다.

예를 들어 아래 payload가 있으면:

```json
{
  "sub": "1",
  "iat": 1773800000,
  "exp": 1773800900
}
```

여기서:

- `sub`
- `iat`
- `exp`

각각이 클레임이다.

쉽게 말하면:

- JWT = 토큰
- Claim = 토큰 안에 담긴 필드

---

## 3. 현재 프로젝트의 JWT 구조

현재 프로젝트의 `access token` 생성 코드는 [`JwtProvider.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java)에 있다.

핵심 메서드:

- [`createAccessToken(Long userId, Duration ttl)`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java#L32)

현재 들어가는 클레임은 사실상 아래 3개다.

- `sub`
  - 사용자 식별자
  - 현재 프로젝트에서는 `userId`
- `iat`
  - 발급 시간
- `exp`
  - 만료 시간

예상 payload 예시:

```json
{
  "sub": "1",
  "iat": 1773880000,
  "exp": 1773880900
}
```

즉 현재 프로젝트의 access token은 비교적 단순한 구조다.

---

## 4. 자주 쓰는 표준 클레임

JWT에는 `sub`, `iat`, `exp` 외에도 자주 쓰는 표준 클레임이 있다.

- `iss`
  - issuer
  - 누가 토큰을 발급했는지
- `aud`
  - audience
  - 이 토큰이 누구를 대상으로 발급됐는지
- `sub`
  - subject
  - 토큰 주체(사용자 등)
- `iat`
  - issued at
  - 발급 시각
- `exp`
  - expiration
  - 만료 시각
- `nbf`
  - not before
  - 이 시각 전에는 사용 불가
- `jti`
  - JWT ID
  - 토큰 고유 식별자

---

## 5. 커스텀 클레임이란?

JWT에는 표준 클레임 외에도 서비스가 직접 정의한 값을 넣을 수 있다.  
이걸 보통 커스텀 클레임이라고 부른다.

예:

```json
{
  "sub": "1",
  "role": "USER",
  "username": "whheo",
  "tokenType": "access"
}
```

여기서:

- `role`
- `username`
- `tokenType`

같은 값은 서비스가 필요에 따라 넣는 커스텀 클레임이다.

주의할 점:

- 커스텀 클레임은 넣을 수 있지만, 너무 많은 정보를 넣으면 토큰이 커진다.
- 민감한 정보는 JWT에 넣지 않는 편이 좋다.
- 권한이 자주 바뀌는 값은 토큰에 넣을 때 신중해야 한다.

---

## 6. 현재 프로젝트에서 추가를 고려할 만한 클레임

현재 프로젝트에서 보안을 한 단계 강화하려면 access token에 아래 값을 추가하는 방식을 고려할 수 있다.

- `iss`
- `aud`
- `tokenType`
- `jti`

### 6-1. `iss`

예:

```json
"iss": "onfilm-api"
```

의미:

- 이 토큰이 우리 API 서버가 발급한 토큰인지 확인

효과:

- 다른 시스템에서 발급된 토큰이 섞이는 문제를 줄일 수 있음

### 6-2. `aud`

예:

```json
"aud": "onfilm-web"
```

의미:

- 이 토큰이 어떤 클라이언트/서비스를 대상으로 발급됐는지 표시

효과:

- 웹용 토큰과 다른 소비자를 구분하기 쉬움

### 6-3. `tokenType`

예:

```json
"tokenType": "access"
```

의미:

- 이 토큰이 access token인지 refresh token인지 구분

효과:

- access 전용 검증 로직에서 refresh token이 잘못 사용되는 문제를 줄일 수 있음

### 6-4. `jti`

예:

```json
"jti": "a7d7b3f6-3e4d-4f0d-a2e1-2f4c87ef8f8d"
```

의미:

- 토큰 고유 ID

효과:

- 블랙리스트 처리
- 특정 토큰 무효화
- 추적/로그 분석

---

## 7. 커스텀 클레임 추가 후 access token 예시

예상 payload:

```json
{
  "iss": "onfilm-api",
  "aud": "onfilm-web",
  "sub": "1",
  "tokenType": "access",
  "jti": "a7d7b3f6-3e4d-4f0d-a2e1-2f4c87ef8f8d",
  "iat": 1773880000,
  "exp": 1773880900
}
```

즉 현재보다 “누가 발급했고”, “누구 대상이며”, “어떤 타입 토큰이고”, “이 토큰 고유 ID가 무엇인지”까지 명확해진다.

---

## 8. createAccessToken은 어떻게 바뀌는가

현재는 대략 이런 구조다.

```java
public String createAccessToken(Long userId, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
}
```

커스텀 클레임을 추가하면 예를 들어 아래처럼 바뀔 수 있다.

```java
public String createAccessToken(Long userId, Duration ttl) {
    Instant now = Instant.now();
    String jti = UUID.randomUUID().toString();

    return Jwts.builder()
            .issuer("onfilm-api")
            .audience().add("onfilm-web").and()
            .subject(String.valueOf(userId))
            .claim("tokenType", "access")
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
}
```

의미:

- `.issuer("onfilm-api")` -> `iss`
- `.audience().add("onfilm-web")` -> `aud`
- `.claim("tokenType", "access")` -> 커스텀 클레임
- `.id(jti)` -> `jti`

---

## 9. 검증 로직은 어떻게 달라지는가

현재 프로젝트의 검증 로직은 [`validate(String token)`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java#L48) 기준으로 보면 사실상 아래만 본다.

- 서명이 유효한가
- 형식이 올바른가
- 만료되지 않았는가

즉 현재는 `iss`, `aud`, `tokenType`, `jti`를 검증하지 않는다.

### 9-1. 현재 검증 로직 개념

```java
public boolean validate(String token) {
    try {
        Jwts.parser().verifyWith((SecretKey) key).build().parseSignedClaims(token);
        return true;
    } catch (JwtException | IllegalArgumentException e) {
        return false;
    }
}
```

### 9-2. 커스텀 클레임 추가 후 검증 포인트

access token 검증 시 아래를 추가로 확인하는 것이 좋다.

- `iss == "onfilm-api"`
- `aud` 안에 `"onfilm-web"` 포함
- `tokenType == "access"`
- `jti` 존재 여부

즉 단순히 “서명이 맞는 JWT”가 아니라, “우리 서비스가 발급한 웹용 access token”인지까지 검증하는 구조로 바뀐다.

### 9-3. 예시 검증 로직

```java
public boolean validateAccessToken(String token) {
    try {
        Claims claims = Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"onfilm-api".equals(claims.getIssuer())) {
            return false;
        }

        Object audience = claims.get("aud");
        if (audience == null || !audience.toString().contains("onfilm-web")) {
            return false;
        }

        String tokenType = claims.get("tokenType", String.class);
        if (!"access".equals(tokenType)) {
            return false;
        }

        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            return false;
        }

        return true;
    } catch (JwtException | IllegalArgumentException e) {
        return false;
    }
}
```

실제로는 `aud`를 어떤 타입으로 파싱할지 라이브러리 버전에 맞춰 조정해야 하지만, 개념은 위와 같다.

---

## 10. 검증 로직 변경 시 같이 생각할 점

### 10-1. `validate()`를 하나로 둘지, access 전용으로 나눌지

지금은 `validate(String token)` 하나만 있다.  
하지만 커스텀 클레임이 들어가면 보통 아래처럼 역할을 나누는 편이 더 명확하다.

- `validateAccessToken()`
- `validateRefreshToken()` 또는 refresh는 별도 DB 검증

현재 프로젝트는 refresh token이 JWT가 아니라 랜덤 문자열 기반이라, 실무적으로는 `validateAccessToken()` 전용 메서드로 바꾸는 방향이 더 자연스럽다.

### 10-2. `parseUserId()`도 access token 전제로 동작하게 만들 수 있음

현재는 `sub`만 읽어 바로 `Long`으로 변환한다.

커스텀 클레임을 넣으면:

- 먼저 access token 검증
- 그 다음 `sub` 파싱

순서로 가는 것이 더 안전하다.

### 10-3. `jti`를 실제로 활용하려면 저장소가 필요함

`jti`를 넣는 것만으로는 충분하지 않다.  
실제로 아래 기능을 하려면 별도 저장소가 필요하다.

- 블랙리스트
- 강제 로그아웃
- 탈취 의심 토큰 폐기

즉 `jti`는 “추적/무효화 확장성”을 위한 기반이다.

---

## 11. 현재 프로젝트에 추천하는 현실적인 확장 방향

현재 구조를 크게 깨지 않으면서 강화하려면 이 정도가 적당하다.

### access token

- `sub`
- `iat`
- `exp`
- `iss`
- `aud`
- `tokenType`
- `jti`

### refresh token

- 지금처럼 JWT가 아닌 랜덤 문자열 유지
- DB에 해시 저장
- rotation 유지

### 검증

- `validate()`를 `validateAccessToken()`으로 명확히 분리
- `iss`, `aud`, `tokenType`, `jti` 확인

---

## 12. 면접에서 말하는 방식

아래처럼 설명하면 좋다.

`현재 프로젝트의 access token은 userId를 subject로 담는 비교적 단순한 JWT 구조입니다. 서명과 만료 시간 중심으로 검증하기 때문에 구현은 단순하지만, 보안을 더 강화하려면 issuer, audience, token type, jti 같은 클레임을 추가하고 access token 검증 로직에서도 이 값을 함께 확인하도록 설계할 수 있습니다. refresh token은 JWT로 만들기보다 지금처럼 DB 기반 random token rotation 구조를 유지하는 쪽이 더 적절하다고 봤습니다.`

---

## 13. 한 줄 정리

JWT 클레임은 토큰 안의 데이터 필드이고, 현재 프로젝트는 `sub/iat/exp` 중심의 단순한 access token 구조다.  
보안을 더 강화하려면 `iss`, `aud`, `tokenType`, `jti`를 추가하고, 검증 로직도 “서명 + 만료” 수준에서 “발급자 + 대상 + 타입 + 토큰 고유성”까지 확인하는 방향으로 확장할 수 있다.
