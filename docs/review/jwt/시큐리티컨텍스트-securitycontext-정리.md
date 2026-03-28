# 시큐리티컨텍스트 SecurityContext 정리

## 1. SecurityContext란?

`SecurityContext`는 Spring Security에서 현재 요청의 인증 정보를 담아두는 공간이다.

쉽게 말하면:

- `Authentication` = 로그인한 사용자 정보 객체
- `SecurityContext` = 그 `Authentication`을 담아두는 자리

즉 비유하면:

- `Authentication`은 신분증
- `SecurityContext`는 그 신분증을 넣어두는 지갑

이라고 이해하면 된다.

---

## 2. 왜 필요한가

JWT 토큰을 검증해서 현재 사용자가 누구인지 알아냈다고 해도, 그 정보를 어딘가에 저장해두지 않으면 이후 컨트롤러나 서비스가 현재 사용자를 알 수 없다.

그래서 Spring Security는 현재 요청 동안 사용할 인증 정보를 `SecurityContext`에 저장해두는 구조를 쓴다.

현재 프로젝트에서도 JWT 필터가 토큰을 검증한 뒤:

```java
Authentication authentication = jwtProvider.getAuthentication(token);
SecurityContextHolder.getContext().setAuthentication(authentication);
```

이렇게 `Authentication`을 `SecurityContext`에 저장한다.

---

## 3. SecurityContextHolder는 무엇인가

`SecurityContext`를 직접 꺼내고 저장할 때 사용하는 도구가 `SecurityContextHolder`다.

보통 이렇게 쓴다.

```java
SecurityContextHolder.getContext()
```

이 코드는 현재 요청에 연결된 `SecurityContext`를 가져온다.

즉:

- `SecurityContextHolder`
  - 현재 요청의 보안 컨텍스트 접근 도구
- `SecurityContext`
  - 인증 정보를 담는 실제 객체

이다.

---

## 4. 현재 프로젝트에서는 어떻게 동작하는가

현재 프로젝트의 JWT 인증 흐름은 대략 아래와 같다.

1. 브라우저가 access token 쿠키를 보낸다
2. [`JwtAuthFilter.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtAuthFilter.java)가 실행된다
3. 필터가 쿠키 또는 Authorization 헤더에서 토큰을 읽는다
4. [`JwtProvider.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java)로 토큰을 검증한다
5. 유효하면 `Authentication` 객체를 만든다
6. 그 `Authentication`을 `SecurityContextHolder.getContext().setAuthentication(...)`으로 저장한다
7. 이후 컨트롤러/서비스는 현재 로그인한 사용자 정보를 사용할 수 있다

즉 `SecurityContext`는 “현재 요청이 진행되는 동안 인증 결과를 공유하는 저장소” 역할을 한다.

---

## 5. 요청이 끝난 뒤에도 계속 남아있는가?

현재 프로젝트는 `JWT + stateless` 구조이므로, `SecurityContext`는 요청이 끝난 뒤 장기적으로 유지되는 로그인 저장소가 아니다.

즉:

- 요청 A가 들어오면
  - JWT를 검증하고
  - `Authentication`을 만들고
  - `SecurityContext`에 넣는다

- 요청 A가 끝나면
  - 그 요청의 `SecurityContext`도 끝난다

- 요청 B가 들어오면
  - 다시 JWT를 읽고
  - 다시 검증하고
  - 다시 `Authentication`을 만들고
  - 다시 `SecurityContext`에 넣는다

즉 이전 요청의 인증 객체를 다음 요청이 그대로 재사용하는 구조는 아니다.

---

## 6. 세션 저장소와는 무엇이 다른가

`SecurityContext`는 현재 요청에서 사용하는 보안 컨텍스트이고, 세션 저장소처럼 로그인 상태를 오래 보관하는 서버 저장소와는 다르다.

구분하면:

### 세션 방식

- 서버가 로그인 상태를 세션 저장소에 유지
- 이후 요청이 오면 세션 ID로 로그인 상태를 꺼냄

### JWT 방식

- 서버가 로그인 상태를 장기 저장하지 않음
- 요청마다 토큰을 검증해서 인증 상태를 복원
- 복원한 결과를 그 요청 동안만 `SecurityContext`에 저장

즉 현재 프로젝트의 `SecurityContext`는 세션 저장소 대체물이 아니라, “요청 단위 인증 결과를 담는 컨텍스트”라고 보는 것이 맞다.

---

## 7. 컨트롤러에서 Authentication을 바로 받을 수 있는 이유

예를 들어:

```java
@GetMapping("/me")
public ResponseEntity<MeResponse> me(Authentication authentication)
```

이렇게 컨트롤러 메서드에서 `Authentication`을 직접 받을 수 있는 이유는, 앞단 필터가 이미 `SecurityContext`에 인증 객체를 넣어뒀기 때문이다.

Spring Security는 현재 요청의 `SecurityContext`를 보고:

- 이 요청이 인증된 요청인지 확인하고
- 거기 들어있는 `Authentication`을 컨트롤러 파라미터로 주입한다

즉 `Authentication` 파라미터 주입은 결국 `SecurityContext` 덕분에 가능한 것이다.

---

## 8. 서비스 코드에서는 어떻게 사용하는가

직접 `Authentication`을 파라미터로 받지 않더라도, 서비스나 유틸 클래스에서 `SecurityContextHolder`를 통해 현재 사용자를 읽을 수 있다.

현재 프로젝트에서는 [`SecurityUtil.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/util/SecurityUtil.java) 같은 유틸을 통해 현재 사용자 ID를 꺼내는 방식도 사용한다.

즉 흐름은:

- 필터가 `SecurityContext`에 넣음
- 컨트롤러나 유틸이 `SecurityContext`에서 꺼냄

이다.

---

## 9. 현재 프로젝트 기준 핵심 관계

현재 프로젝트의 인증 관련 핵심 관계는 이렇게 정리할 수 있다.

- [`JwtAuthFilter.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtAuthFilter.java)
  - 요청마다 실행
  - JWT 읽기 / 검증 / Authentication 생성

- [`JwtProvider.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java)
  - JWT에서 userId를 꺼내고 `Authentication` 생성

- `Authentication`
  - 현재 로그인한 사용자 정보 객체

- `SecurityContext`
  - 그 `Authentication`을 담아두는 요청 단위 저장소

- `SecurityContextHolder`
  - 현재 요청의 `SecurityContext`를 꺼내고 저장하는 도구

---

## 10. 한 줄 흐름 요약

`JWT 검증 -> Authentication 생성 -> SecurityContext 저장 -> 컨트롤러/서비스에서 현재 사용자 사용`

현재 프로젝트는 이 흐름으로 인증 상태를 처리한다.

---

## 11. 한 줄 정리

`SecurityContext`는 현재 요청에서 인증된 사용자 정보를 담아두는 공간이고, `JwtAuthFilter`가 만든 `Authentication`을 저장해 이후 컨트롤러와 서비스가 현재 로그인한 사용자를 사용할 수 있게 해주는 구조다.
