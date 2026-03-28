# 시큐리티컨피그 SecurityConfig 정리

## 1. SecurityConfig란?

`SecurityConfig`는 Spring Security에서 보안 정책을 설정하는 클래스다.

쉽게 말하면 이 클래스는 다음을 정한다.

- 어떤 요청을 인증 없이 허용할지
- 어떤 요청은 로그인한 사용자만 허용할지
- 세션을 쓸지 말지
- 어떤 필터를 어떤 순서로 실행할지
- 인증 실패 / 권한 실패 시 어떤 상태코드를 줄지

즉 `SecurityConfig`는 보안 규칙 자체를 정의하는 곳이다.

---

## 2. 현재 프로젝트에서 이 메서드가 하는 일

예를 들어 `dev` 환경에서는 [`SecurityConfig.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/SecurityConfig.java)의 아래 메서드가 보안 체인을 만든다.

```java
@Bean
@Profile("dev")
SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception
```

의미:

- `@Bean`
  - 이 메서드가 반환하는 보안 체인을 Spring Bean으로 등록
- `@Profile("dev")`
  - `dev` 프로필에서만 이 설정 사용
- `SecurityFilterChain`
  - 실제 요청마다 실행되는 보안 필터 체인

즉 이 메서드는 `dev 환경용 보안 체인 생성기`다.

---

## 3. SecurityConfig와 SecurityFilterChain의 관계

헷갈리기 쉬운 부분은 `SecurityConfig`와 `SecurityFilterChain`이 같은 것이 아니라는 점이다.

구분하면:

- `SecurityConfig`
  - 설정 클래스
  - 앱 시작 시 보안 규칙을 조립함

- `SecurityFilterChain`
  - 실제 요청이 들어올 때 실행되는 필터 체인

즉:

1. 앱 시작 시 `SecurityConfig`가 실행되어
2. `SecurityFilterChain`을 만들고 Bean으로 등록하고
3. 이후 실제 API 요청이 들어오면 그 `SecurityFilterChain`이 동작한다

---

## 4. 이 메서드 안에서 설정하는 항목들

## 4-1. 예외 처리

```java
MvcRequestMatcher publicProfileMatcher =
        new MvcRequestMatcher(handlerMappingIntrospector, "/{username:[a-zA-Z0-9_-]{3,20}}");
```

이 부분은 URL 패턴을 Spring MVC 방식으로 정확히 매칭하기 위한 객체를 미리 만들어두는 코드다.

즉 아래 같은 경로를 보안 정책에서 재사용하려고 선언한 것이다.

- `/{username}`
- `/{username}/edit-profile`
- `/{username}/edit-filmography`
- `/{username}/edit-gallery`
- `/{username}/storyboard`

왜 `MvcRequestMatcher`를 쓰냐면:

- 단순 문자열 경로가 아니라
- Spring MVC가 실제로 해석하는 경로 규칙과 맞춰서
- PathVariable과 정규식까지 포함한 URL 매칭을 하고 싶기 때문이다

예를 들어:

```java
"/{username:[a-zA-Z0-9_-]{3,20}}"
```

이건:

- username이라는 path variable이고
- 영문/숫자/언더스코어/하이픈만 허용하고
- 길이는 3~20자여야 한다

는 뜻이다.

즉 이 matcher를 만들어두면, 이후 `authorizeHttpRequests`에서 해당 pretty URL들을 `permitAll()` 또는 `authenticated()` 규칙에 정확하게 연결할 수 있다.

---

## 4-2. 예외 처리

```java
.exceptionHandling(e -> e
    .authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(401);
    })
    .accessDeniedHandler((request, response, accessDeniedException) -> {
        response.setStatus(403);
    })
)
```

이 부분은 보안 예외가 났을 때 어떤 응답을 줄지 정한다.

- 인증이 안 된 사용자가 인증 필요한 요청을 하면 `401`
- 인증은 됐지만 권한이 부족하면 `403`

즉 보안 실패 시 응답 정책을 정하는 부분이다.

---

## 4-3. 세션 정책

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

이 부분은 아주 중요하다.

뜻:

- 서버 세션을 사용하지 않겠다
- 로그인 상태를 서버 메모리/세션 저장소에 보관하지 않겠다

즉 현재 프로젝트는 `JWT 기반 stateless 인증 구조`라는 의미다.

그래서 요청마다 access token을 다시 검증해야 한다.

---

## 4-4. 기본 로그인 방식 비활성화

```java
.formLogin(AbstractHttpConfigurer::disable)
.httpBasic(AbstractHttpConfigurer::disable)
```

뜻:

- Spring 기본 로그인 폼 사용 안 함
- HTTP Basic 인증 안 씀

즉 이 프로젝트는 직접 구현한 `/auth/login` + JWT 방식을 사용한다는 의미다.

---

## 4-5. 헤더 설정

```java
.headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
```

`dev`에서 H2 콘솔 iframe 접근을 허용하기 위한 설정이다.

즉 개발 환경 편의용 설정에 가깝다.

---

## 4-6. CSRF 설정

```java
.csrf(AbstractHttpConfigurer::disable)
```

이건 Spring Security 기본 CSRF 기능을 끄는 설정이다.

다만 현재 프로젝트는 CSRF를 완전히 무시하는 것은 아니고, 별도 커스텀 필터(`CsrfProtectionFilter`)를 사용하고 있다.

즉 구조는:

- Spring 기본 CSRF는 끔
- 프로젝트 전용 CSRF 필터로 직접 제어

이다.

---

## 4-7. URL 권한 정책

```java
.authorizeHttpRequests(auth -> auth ...)
```

이 부분은 어떤 URL을 허용하고 어떤 URL을 인증 필요하게 둘지 정하는 핵심이다.

예를 들면:

- `/files/**` -> 누구나 허용
- `/auth/**` -> 로그인 전에도 허용
- `/auth/me` -> 로그인 필요
- 나머지 대부분 -> 인증 필요

즉 `permitAll()`과 `authenticated()`를 통해 URL 접근 정책을 정하는 부분이다.

---

## 4-8. 커스텀 필터 순서

```java
.addFilterBefore(authPageBlockFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

이 부분은 커스텀 필터를 어떤 순서로 체인에 넣을지 정하는 코드다.

뜻은:

`UsernamePasswordAuthenticationFilter보다 앞에서 이 필터들을 실행해라`

는 의미다.

즉 실제 요청이 들어오면 이 필터들이 먼저 돈다.

---

## 5. API 요청이 왔을 때 access token 검증은 어떻게 일어나는가

이 부분이 가장 중요하다.

현재 프로젝트에서 access token 검증은 `SecurityConfig`가 직접 하는 것이 아니라, `SecurityConfig`가 등록한 필터 체인을 통해 일어난다.

흐름을 쓰면:

1. 앱 시작 시 `SecurityConfig`가 `SecurityFilterChain`을 만든다
2. 그 체인 안에 `jwtAuthFilter`를 등록한다
3. 클라이언트가 API 요청을 보낸다
4. Spring Security가 등록된 `SecurityFilterChain`을 실행한다
5. 체인 안의 `jwtAuthFilter`가 실행된다
6. `jwtAuthFilter`가 쿠키 또는 Authorization 헤더에서 access token을 읽는다
7. [`JwtProvider.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/config/JwtProvider.java)로 토큰을 검증한다
8. 유효하면 `Authentication`을 만들고 `SecurityContextHolder`에 넣는다
9. 이후 컨트롤러/서비스는 현재 로그인한 사용자 정보를 사용할 수 있다

즉 `SecurityConfig`는 access token을 직접 검증하는 코드가 아니라,

- access token을 검증할 필터를
- 어떤 순서로
- 어떤 요청 흐름 안에 넣을지

를 정의하는 코드다.

한 줄로 말하면:

`SecurityConfig에서 만든 SecurityFilterChain이 실제 요청마다 실행되고, 그 안에 포함된 JwtAuthFilter가 access token을 검증한다.`

---

## 6. 필터는 어떤 식으로 만들어야 하는가

Spring Security에서 커스텀 필터는 보통 `OncePerRequestFilter`를 상속해서 만든다.

형태는 보통 이렇다.

```java
public class XxxFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. 요청에서 필요한 값 읽기
        // 2. 검사 / 검증 / 차단 여부 판단
        // 3. 필요하면 상태 세팅
        // 4. 다음 필터로 넘기기
        filterChain.doFilter(request, response);
    }
}
```

즉 커스텀 필터의 기본 구조는:

1. 요청을 읽고
2. 검사하고
3. 필요하면 인증/보안 상태를 설정하고
4. 다음 필터로 넘기는 구조다

---

## 7. 필터를 만들 때 지켜야 할 원칙

## 7-1. 필터 하나는 책임 하나

좋은 예:

- JWT 인증 필터
- CSRF 검사 필터
- 내부 API 키 필터

나쁜 예:

- 토큰 검증, 권한 검사, DB 업데이트, 로깅까지 한 필터에 다 넣는 경우

즉 필터도 단일 책임 원칙이 중요하다.

---

## 7-2. 가능한 한 가볍게 유지

필터는 모든 요청마다 돌 수 있으므로 무거우면 안 된다.

예를 들어:

- 토큰 읽기
- 검증
- SecurityContext 세팅

정도는 괜찮지만,

- 복잡한 비즈니스 로직
- 과도한 DB 조회
- 큰 연산

은 필터에 두지 않는 편이 좋다.

---

## 7-3. 통과시킬지 막을지 명확해야 함

필터는 보통 두 가지 중 하나를 한다.

- 그냥 통과
- 여기서 차단하고 응답 종료

예:

- JWT 없으면 그냥 다음으로 넘길지
- CSRF 실패면 바로 403 줄지

이 정책이 코드에서 명확해야 한다.

---

## 7-4. `filterChain.doFilter()`를 의식해야 함

이 호출은 매우 중요하다.

- 호출하면 다음 필터/컨트롤러로 넘어감
- 호출하지 않으면 여기서 요청 처리 종료

즉 필터를 작성할 때 가장 중요한 줄 중 하나다.

---

## 8. 필터 순서는 어떻게 정하는가

정해진 절대 공식이 있는 것은 아니지만, 보통은 다음 기준으로 정한다.

### 기준

- 무엇을 먼저 알아야 다음 단계가 의미 있는가
- 무엇을 먼저 막아야 안전한가

현재 프로젝트 흐름을 단순화하면:

1. 차단성 필터
2. CSRF 같은 요청 안전성 검사
3. JWT 인증 필터
4. 이후 인가 / 컨트롤러

즉 먼저 막을 건 먼저 막고, 인증 정보는 그 다음 만들고, 그 뒤 비즈니스 로직으로 보내는 구조가 자연스럽다.

---

## 9. HttpSecurity에서 무엇을 설정할 수 있는가

`HttpSecurity`는 보안 정책 조립기 같은 객체다.

보통 이런 것을 설정한다.

- `sessionManagement`
  - 세션 사용 여부
- `csrf`
  - CSRF 정책
- `cors`
  - CORS 정책
- `formLogin`
  - 기본 로그인 폼 사용 여부
- `httpBasic`
  - Basic 인증 사용 여부
- `headers`
  - frame options, CSP 등
- `authorizeHttpRequests`
  - URL별 접근 정책
- `exceptionHandling`
  - 401/403 처리 방식
- `addFilterBefore / addFilterAfter`
  - 커스텀 필터 순서

즉 `HttpSecurity`를 통해 프로젝트 전체 보안 정책을 조립한다고 이해하면 된다.

---

## 10. 현재 코드에서 꼭 이해해야 할 핵심

이 메서드는 단순히 JWT 필터 하나만 붙이는 코드가 아니다.

실제로는 다음 전체를 정의한다.

- 세션 안 씀
- 기본 폼 로그인 안 씀
- 어떤 URL은 공개
- 어떤 URL은 인증 필요
- 보안 실패 시 어떤 상태코드 반환
- 어떤 필터를 어떤 순서로 실행

즉 `SecurityConfig`는 프로젝트 보안 정책 전체를 조립하는 코드다.

---

## 11. 한 줄 정리

`SecurityConfig`는 Spring Security 보안 정책을 조립하는 설정 클래스이고, 여기서 만들어진 `SecurityFilterChain`이 실제 API 요청마다 실행되며, 그 체인 안의 `JwtAuthFilter`가 access token을 검증한다.
