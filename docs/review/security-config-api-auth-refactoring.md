# SecurityConfig API 접근 제어 분리

## 문제

`/api/people/**`가 통째로 `permitAll()`로 열려 있었어요.

```java
// 수정 전
new AntPathRequestMatcher("/api/people/**")  // 모든 /api/people/** 허용
```

공개 조회 API와 인증이 필요한 write API가 같은 경로 prefix를 쓰고 있어서 구분 없이 전부 열어둔 상태였어요.

---

## 문제가 되는 흐름

비로그인 유저가 write API(스토리보드 생성, 갤러리 수정 등)를 호출하면:

```
비로그인 유저
    ↓
JwtAuthFilter → 토큰 없음 → SecurityContext 비어있음
    ↓
SecurityConfig → /api/people/** permitAll → 통과
    ↓
Controller
    ↓
Service → findCurrentPersonId() → IllegalStateException 발생
```

필터에서 막아야 할 요청이 서비스 레이어까지 도달해서 불필요한 처리가 발생했어요.

---

## 수정 내용

공개 API(GET 조회)와 인증 필요 API를 경로와 HTTP 메서드 기준으로 분리했어요.

```java
// 수정 후

// 공개 API - GET 조회만 허용
.requestMatchers("/api/person/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/people/*").permitAll()          // 프로필 조회
.requestMatchers(HttpMethod.GET, "/api/people/*/movies").permitAll()   // 필모그래피 조회
.requestMatchers(HttpMethod.GET, "/api/people/*/gallery").permitAll()  // 갤러리 조회
.requestMatchers(HttpMethod.GET, "/api/people/*/filmography").permitAll() // 필모그래피 파일

// 나머지 /api/people/** write 엔드포인트는 인증 필요
.requestMatchers("/api/people/**").authenticated()
```

---

## 수정 후 흐름

```
비로그인 유저 → write API 호출
    ↓
JwtAuthFilter → 토큰 없음 → SecurityContext 비어있음
    ↓
SecurityConfig → /api/people/** authenticated → 401 반환
    ↓
컨트롤러/서비스까지 도달하지 않음
```

---

## SecurityContextHolder 역할

필터 → Security 검증 → 서비스를 연결하는 공유 저장소예요.

```
JwtAuthFilter
    → access_token 유효
    → SecurityContextHolder.getContext().setAuthentication(auth) 저장
    ↓
SecurityConfig authorizeHttpRequests
    → authenticated() 경로
    → SecurityContextHolder에서 Authentication 꺼냄
    → 있으면 컨트롤러 통과 / 없으면 401
    ↓
Service
    → SecurityUtil.currentUserId()
    → SecurityContextHolder.getContext().getAuthentication() 꺼내서 userId 추출
```

---

## 수정 전 → 수정 후 비교

| | 수정 전 | 수정 후 |
|---|---|---|
| `/api/people/**` 전체 | permitAll | 공개 GET만 permitAll |
| write API 비로그인 요청 | 서비스까지 도달 후 예외 | 필터에서 401 차단 |
| 인증 검증 위치 | 서비스 레이어 | JwtAuthFilter + SecurityConfig |

---

## 공개/인증 엔드포인트 정리

| 엔드포인트 | 메서드 | 접근 제어 |
|---|---|---|
| `/api/person/**` | 전체 | permitAll (username → publicId 조회) |
| `/api/people/{publicId}` | GET | permitAll (프로필 조회) |
| `/api/people/{publicId}/movies` | GET | permitAll (필모그래피 조회) |
| `/api/people/{publicId}/gallery` | GET | permitAll (갤러리 조회) |
| `/api/people/{publicId}/filmography` | GET | permitAll (필모그래피 파일) |
| `/api/people/**` 나머지 | 전체 | authenticated |
