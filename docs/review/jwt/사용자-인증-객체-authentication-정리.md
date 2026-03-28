# 사용자 인증 객체 Authentication 정리

## 1. Authentication이란?

`Authentication`은 Spring Security에서 현재 요청의 로그인 상태를 표현하는 객체다.

쉽게 말하면:

- 이 요청이 로그인한 사용자의 요청인지
- 로그인했다면 누구의 요청인지
- 어떤 권한을 가졌는지

를 담는 객체다.

즉 `Authentication`은 현재 요청의 `신분증` 같은 역할을 한다.

---

## 2. 왜 필요한가

JWT 토큰을 검증했다고 해서 Spring Security가 자동으로 “이 사용자는 로그인된 사용자다”라고 이해하는 것은 아니다.

그래서 토큰 검증이 끝난 뒤, 서버는 그 결과를 `Authentication` 객체로 만들어서 Spring Security에 등록해야 한다.

현재 프로젝트에서는 [`JwtAuthFilter.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtAuthFilter.java)에서 이 작업을 한다.

```java
Authentication authentication = jwtProvider.getAuthentication(token);
SecurityContextHolder.getContext().setAuthentication(authentication);
```

이 뜻은:

- 토큰이 유효하므로
- 이 요청을 로그인된 사용자 요청으로 등록한다

는 의미다.

---

## 3. Authentication 안에는 무엇이 들어가는가

일반적으로 `Authentication` 객체에는 아래 정보가 들어간다.

- `principal`
  - 현재 사용자 정보
- `credentials`
  - 인증 수단
- `authorities`
  - 권한 목록
- `authenticated`
  - 인증 여부

즉 “누가, 어떤 방식으로 인증됐고, 어떤 권한을 갖는지”를 담는 객체라고 보면 된다.

---

## 4. 현재 프로젝트에서는 어떻게 만들고 있는가

현재 프로젝트에서는 [`JwtProvider.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java)의 `getAuthentication()` 메서드에서 `Authentication` 객체를 만든다.

```java
public Authentication getAuthentication(String token) {
    Long userId = parseUserId(token);

    User principal = new User(String.valueOf(userId), "", List.of());
    return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
}
```

이 코드를 풀면:

- JWT에서 `userId`를 꺼낸다
- Spring Security의 `User` 객체를 하나 만든다
- 그 안의 username 자리에 `userId`를 문자열로 넣는다
- `UsernamePasswordAuthenticationToken`으로 감싼다

즉 현재 프로젝트의 `Authentication`은 사실상:

- principal = `userId`
- credentials = JWT token
- authorities = 빈 리스트

라고 이해하면 된다.

---

## 5. principal이란?

`principal`은 현재 사용자 본체라고 보면 된다.

프로젝트에 따라 principal에는 다음 중 하나가 들어갈 수 있다.

- username
- userId
- UserDetails
- 커스텀 사용자 객체

현재 프로젝트에서는 Spring Security의 `User` 객체를 쓰고 있고, 그 안의 username 자리에 `userId`를 넣고 있다.

즉 사실상 principal은 “현재 로그인한 사용자의 userId를 담고 있는 객체”다.

그래서 이후 코드에서:

```java
authentication.getName()
```

을 호출하면 `userId` 문자열이 나온다.

---

## 6. credentials란?

`credentials`는 인증에 사용된 값이다.

예를 들면:

- 로그인 전에는 비밀번호
- JWT 인증에서는 access token

현재 프로젝트는 JWT 기반이므로:

- credentials = JWT token

이다.

---

## 7. authorities란?

`authorities`는 권한 목록이다.

예:

- `ROLE_USER`
- `ROLE_ADMIN`

현재 프로젝트에서는:

```java
List.of()
```

즉 권한 정보를 넣지 않고 있다.

그래서 현재 구조는:

- 로그인 여부는 구분 가능
- 관리자/일반 사용자 같은 역할 기반 권한 관리는 Authentication에 담지 않음

이라고 볼 수 있다.

---

## 8. 왜 UsernamePasswordAuthenticationToken을 쓰는가

Spring Security에서 가장 많이 쓰는 `Authentication` 구현체 중 하나가 `UsernamePasswordAuthenticationToken`이다.

원래는 username/password 로그인에 자주 쓰이지만, JWT 인증에서도 많이 사용한다.

이유는:

- principal
- credentials
- authorities

를 담기 편하기 때문이다.

즉 이 프로젝트에서는 “현재 요청을 인증된 사용자 요청으로 표현하는 표준 컨테이너”로 쓰고 있는 셈이다.

---

## 9. SecurityContextHolder와의 관계

`Authentication` 객체를 만든 뒤에는 `SecurityContextHolder`에 넣는다.

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
```

이렇게 하면 그 이후부터는 Spring Security가 현재 요청을 인증된 요청으로 본다.

그 결과:

- 컨트롤러에서 `Authentication` 파라미터를 받을 수 있고
- `SecurityUtil.currentUserId()` 같은 코드로 현재 사용자를 꺼낼 수 있고
- 인증 필요한 API 접근 제어가 가능해진다

즉 `Authentication`은 단독으로 의미가 있는 것이 아니라, `SecurityContextHolder`에 등록되면서 효과가 생긴다.

---

## 10. 현재 프로젝트에서 실제로 어디서 쓰이는가

### 10-1. 요청 인증 시

[`JwtAuthFilter.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtAuthFilter.java)에서:

1. 요청에서 access token을 읽고
2. `jwtProvider.validate(token)`으로 검증하고
3. `jwtProvider.getAuthentication(token)`으로 인증 객체를 만들고
4. `SecurityContextHolder`에 저장한다

### 10-2. 컨트롤러에서 현재 사용자 읽을 때

예를 들어 [`AuthController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/auth/controller/AuthController.java)의 `/auth/me`에서는:

```java
public ResponseEntity<MeResponse> me(Authentication authentication)
```

처럼 `Authentication`을 직접 받는다.

이게 가능한 이유는 앞단 필터에서 이미 `SecurityContext`에 인증 객체를 넣어뒀기 때문이다.

---

## 11. 아주 짧은 흐름 요약

현재 프로젝트의 인증 흐름에서 `Authentication`은 이렇게 동작한다.

1. 클라이언트가 access token을 보낸다
2. `JwtAuthFilter`가 토큰을 읽는다
3. `JwtProvider.validate()`로 검증한다
4. `JwtProvider.getAuthentication()`으로 사용자 인증 객체를 만든다
5. `SecurityContextHolder`에 넣는다
6. 이후 컨트롤러/서비스는 현재 사용자를 읽을 수 있다

---

## 12. Authentication은 요청마다 새로 만들어지는가?

현재 프로젝트는 `JWT + STATELESS` 구조이므로, `Authentication`은 요청마다 새로 만들어진다.

즉 흐름은 다음과 같다.

1. 로그인 성공 시 access token / refresh token / csrf token을 쿠키로 내려준다
2. 브라우저는 이후 요청마다 access token 쿠키를 자동으로 함께 보낸다
3. 새로운 요청이 들어올 때마다 `JwtAuthFilter`가 다시 실행된다
4. 필터가 access token을 읽고 다시 검증한다
5. 유효하면 그 요청을 위한 `Authentication` 객체를 새로 만든다
6. 새로 만든 `Authentication`을 `SecurityContextHolder`에 넣는다
7. 요청 처리가 끝나면 그 요청의 `SecurityContext`도 함께 끝난다

즉 중요한 포인트는 다음과 같다.

- 이전 요청에서 만든 `Authentication`을 다음 요청이 재사용하지 않는다
- 다음 요청이 오면 다시 토큰을 읽고, 다시 검증하고, 다시 `Authentication`을 만든다
- 따라서 서버 입장에서는 요청마다 인증 검사가 다시 일어난다

다만 사용자가 매번 로그인할 필요는 없다.

이유는:

- 브라우저가 이미 저장한 쿠키를 다음 요청마다 자동으로 보내주기 때문

즉:

- 사용자는 로그인 1번
- 서버는 요청마다 인증 검증

구조라고 이해하면 된다.

---

## 13. 왜 요청마다 다시 인증하는가

이 프로젝트는 세션 로그인 방식이 아니라 JWT 기반의 stateless 인증 방식이다.

stateless 구조에서는 서버가 “로그인 상태 자체”를 세션 저장소에 들고 있지 않는다.  
즉 서버는 매 요청이 올 때마다:

- 이 토큰이 유효한지
- 만료되지 않았는지
- 위조되지 않았는지

를 다시 확인해야 한다.

그래서 `Authentication`도 매 요청마다 다시 만들어진다.

한 줄로 정리하면:

`서버가 로그인 상태를 저장하지 않기 때문에, 요청마다 토큰 검증을 통해 다시 로그인 상태를 복원하는 구조`
이다.

---

## 14. JWT 구조와 세션 구조 비교

인증 구조를 이해하려면 JWT 방식과 세션 방식을 같이 비교하는 것이 좋다.

### 14-1. 세션 기반 인증

세션 방식은 보통 이렇게 동작한다.

1. 로그인 성공
2. 서버가 세션 저장소에 로그인 상태를 저장
3. 브라우저에는 세션 ID만 담긴 쿠키를 내려줌
4. 이후 요청마다 세션 ID를 보고 서버가 로그인 상태를 꺼내옴

즉 서버가 로그인 상태를 직접 들고 있는 구조다.

### 장점

- 강제 로그아웃이나 세션 만료 처리가 쉽다
- 서버가 상태를 직접 관리하므로 통제력이 높다
- 권한 변경이 생겨도 다음 요청부터 바로 반영하기 쉽다

### 단점

- 서버가 세션 상태를 저장해야 한다
- 서버가 여러 대면 세션 공유 저장소(redis 등)가 필요할 수 있다
- stateless 구조보다 확장성이 불리할 수 있다

---

### 14-2. JWT 기반 인증

JWT 방식은 보통 이렇게 동작한다.

1. 로그인 성공
2. 서버가 access token을 발급
3. 브라우저가 토큰을 저장
4. 이후 요청마다 토큰을 보냄
5. 서버는 토큰을 검증해서 현재 사용자를 식별

즉 서버가 로그인 상태를 따로 저장하지 않고, 토큰 자체를 검증해서 인증을 복원하는 구조다.

### 장점

- 서버가 세션 상태를 저장하지 않아도 된다
- stateless 구조라 확장성이 좋다
- API 서버가 여러 대여도 토큰 검증만 되면 동작하기 쉽다

### 단점

- 요청마다 토큰 검증이 필요하다
- access token을 즉시 강제 무효화하기 어렵다
- logout/탈취 대응은 refresh token 전략, 짧은 TTL, 블랙리스트 등 보완 장치가 필요하다

---

## 15. 현재 프로젝트는 왜 JWT 구조를 채택했는가

현재 프로젝트는 access token에 대해서 JWT 기반 구조를 사용하고 있다.

이 구조를 택한 이유는 다음과 같다.

- 요청마다 빠르게 인증 검증 가능
- 브라우저 기반 환경에서도 쿠키로 자동 전송 가능
- 서버가 세션 저장소 없이도 access token을 검증할 수 있음
- API 서버가 여러 대로 늘어나도 세션 공유 의존도가 낮음

즉 이 프로젝트는 “매 요청 인증은 빠르게, 장기 토큰 통제는 별도로”라는 방향을 택한 구조라고 볼 수 있다.

다만 JWT access token만으로는 장기적인 통제가 어렵기 때문에, refresh token은 별도로 DB 기반 랜덤 토큰으로 관리한다.

즉 현재 프로젝트는 다음 역할 분리를 택한 구조다.

- access token: stateless JWT
- refresh token: stateful DB 관리

이 조합은 웹 서비스에서 자주 쓰이는 현실적인 절충안이다.

---

## 16. 한 줄 정리

`Authentication`은 Spring Security에서 현재 로그인한 사용자를 표현하는 객체이고, 현재 프로젝트에서는 JWT에서 꺼낸 `userId`를 담아 “이 요청은 인증된 사용자 요청이다”라고 표시하는 데 사용한다.
