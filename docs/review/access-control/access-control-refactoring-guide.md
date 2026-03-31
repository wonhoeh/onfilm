# 접근 제어 책임 분산 현황과 리팩토링 방향

이 문서는 현재 프로젝트에서 접근 제어 책임이 어디에 분산되어 있는지 정리하고, 유지보수성과 가독성을 높이기 위해 어떤 식으로 리팩토링할 수 있는지 제안하기 위한 문서다.

## 1. 현재 결론

현재 접근 제어는 한 곳에서 끝나지 않고 아래 3개 레이어에 나뉘어 있다.

- `SecurityConfig`
- 페이지 라우팅 컨트롤러
- 실제 API 컨트롤러/서비스

이 구조는 동작은 맞지만, 보안 정책을 한눈에 파악하기 어렵고, 어떤 요청이 어디서 막히는지 추적 비용이 크다.

## 2. 현재 접근 제어 책임이 분산된 위치

### 2-1. Security 설정

[`SecurityConfig.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/auth/config/SecurityConfig.java)

역할:

- 어떤 경로를 `permitAll()`로 열지 결정
- 어떤 경로를 `authenticated()`로 보호할지 결정
- JWT, CSRF, 인증 페이지 차단 필터 순서 정의

현재 특징:

- 공개 프로필 경로 `/{username}`는 `permitAll()`
- 그런데 `/{username}/edit-*`, `/{username}/storyboard*`도 같이 `permitAll()`
- 실제 수정 API 대부분은 `anyRequest().authenticated()` 또는 개별 로직으로 보호

즉 Security 설정만 보면 일부 비공개 페이지가 공개 경로처럼 보인다.

### 2-2. 페이지 라우팅 컨트롤러

[`UserPrivatePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/UserPrivatePageController.java)

역할:

- `/edit-profile`, `/edit-filmography` 같은 비사용자 scoped 경로를 현재 로그인 사용자 경로로 리다이렉트
- `/{username}/edit-*`, `/{username}/storyboard*` 접근 시
  - 로그인 안 했으면 로그인 페이지로 리다이렉트
  - 현재 로그인 username과 path username이 다르면 `403`
  - 같으면 html forward

핵심 코드:

```java
if (current == null) {
    return "redirect:" + buildLoginRedirect(request);
}
if (current != null && !current.equals(username)) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
}
```

즉 비공개 페이지 접근 제어가 Security가 아니라 페이지 라우팅 컨트롤러에 들어 있다.

### 2-3. API 컨트롤러/서비스

예:

- [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
- [`MovieFileController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/MovieFileController.java)
- [`PersonReadService.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/service/PersonReadService.java)

역할:

- 현재 로그인 사용자 기준으로 실제 수정 권한 확인
- 자원 소유권 검사
- 편집 가능 여부 검사

예:

```java
if (!personReadService.canEditMovie(personId, movieId)) {
    throw new IllegalStateException("FORBIDDEN_MOVIE_ACCESS");
}
```

즉 실제 데이터 수정 권한은 다시 API/서비스 레이어에서 별도로 막고 있다.

## 3. 지금 구조의 장점

완전히 나쁜 구조는 아니다. 현재 방식이 가진 장점도 있다.

- 페이지 접근 시 로그인 페이지로 자연스럽게 리다이렉트할 수 있다
- `/{username}` 기반 pretty URL에서 현재 사용자와 path 사용자를 비교하기 쉽다
- 실제 수정 API는 다시 자원 소유권 검사까지 해서 방어가 한 번 더 된다

즉 현재 구조는 UX 요구사항 때문에 이해 가능한 면이 있다.

## 4. 지금 구조의 문제점

### 4-1. 보안 정책이 한눈에 안 보인다

`SecurityConfig`만 보면 `/{username}/edit-*`가 `permitAll()`이라 공개 경로처럼 보인다.

하지만 실제로는 [`UserPrivatePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/UserPrivatePageController.java) 에서 막고 있다.

즉 “어디서 보호되는지”를 여러 파일을 열어봐야 알 수 있다.

### 4-2. 책임이 분산되어 추적이 어렵다

하나의 요청이 막히는 위치가 여러 군데다.

- Security filter chain
- 페이지 라우팅 컨트롤러
- API 컨트롤러
- 서비스

그래서 `403`이 났을 때 원인을 빠르게 찾기 어렵다.

### 4-3. 불필요한 요청이 안쪽까지 들어간다

비로그인 사용자가 `/{username}/edit-filmography`를 요청해도 Security에서 먼저 차단하지 않고 컨트롤러까지 들어간다.

즉 1차 방어를 Security에서 할 수 있는데도, 더 안쪽 레이어가 그 책임을 일부 떠안고 있다.

### 4-4. 유지보수 시 실수하기 쉽다

새로운 private page를 추가할 때:

- `SecurityConfig` permitAll 목록에도 넣어야 하고
- `UserPrivatePageController`에도 추가해야 하고
- 경우에 따라 프론트 경로도 맞춰야 한다

즉 수정 포인트가 여러 군데로 퍼져 있다.

## 5. 어떤 식으로 리팩토링하면 좋은가

핵심 원칙은 “레이어별 책임을 더 명확히 나누는 것”이다.

추천 책임 분리는 아래와 같다.

- Security
  - 로그인 여부 1차 검증
- Controller
  - 페이지 라우팅과 사용자 친화적 리다이렉트
- Service
  - 실제 자원 소유권/수정 권한 검증

## 6. 추천 리팩토링 방향

### 6-1. private page는 Security에서 1차로 `authenticated()` 처리

현재:

- `/{username}/edit-*`
- `/{username}/storyboard*`

가 `permitAll()`

개선 방향:

- 공개 프로필 `/{username}`만 `permitAll()`
- private page 경로는 `authenticated()`

예시 방향:

- `publicProfileMatcher`만 `permitAll()`
- `userEditProfileMatcher`, `userEditFilmographyMatcher`, `userEditGalleryMatcher`, `userStoryboardMatcher`, `userEditStoryboardMatcher`, `userStoryboardViewMatcher`는 `authenticated()`

이렇게 하면 비로그인 요청은 Security에서 더 빨리 걸러진다.

### 6-2. 컨트롤러는 “본인 페이지인지”와 리다이렉트만 담당

[`UserPrivatePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/UserPrivatePageController.java) 는 계속 필요하다.

이유:

- `/edit-profile` -> `/{username}/edit-profile` 리다이렉트
- 로그인 안 했을 때 `login.html?next=...` 로 보내는 UX 처리
- `path username == current username` 확인

즉 이 컨트롤러는 보안의 전부를 담당하기보다, “private page 라우팅 정책”을 담당하게 두는 게 좋다.

### 6-3. 실제 수정 API는 서비스에서 자원 권한 검증 유지

예:

- 영화 수정
- 파일 업로드
- 스토리보드 수정

이런 API는 로그인만 했다고 끝나면 안 되고, 실제 자원 소유권 검사가 필요하다.

즉 아래는 계속 유지하는 게 맞다.

- `authenticated()`로 1차 보호
- 서비스에서 `canEditMovie`, `currentUserId`, 소유권 검사

## 7. 리팩토링 후 기대 효과

### 7-1. 보안 정책이 읽기 쉬워진다

`SecurityConfig`만 봐도:

- 공개 페이지
- 인증 필요한 private page
- 공개 API
- 인증 필요한 API

구분이 더 명확해진다.

### 7-2. 디버깅이 쉬워진다

비로그인 접근 실패는 Security,
로그인했지만 다른 사람 페이지 접근은 Controller,
로그인했고 본인 페이지지만 자원 권한 없음은 Service

처럼 역할이 더 선명해진다.

### 7-3. 새 private page 추가 시 실수 가능성이 줄어든다

private page 규칙이 Security와 Controller 기준으로 더 일관되게 정리되면, 어떤 파일을 같이 수정해야 하는지 명확해진다.

## 8. 추천 리팩토링 순서

1. `SecurityConfig`에서 `/{username}/edit-*`, `/{username}/storyboard*`를 `authenticated()`로 전환
2. `/{username}` 공개 프로필 matcher만 `permitAll()`로 유지
3. `UserPrivatePageController`는 로그인 리다이렉트와 username 일치 검사만 담당하도록 역할 명시
4. private page 정책을 주석이나 별도 문서로 남겨 일관성 유지
5. 실제 수정 API는 서비스 레이어 권한 검증 유지

## 9. 최종 평가

현재 구조는 “동작은 맞지만 보안 책임이 분산되어 있다”는 점이 핵심이다.

즉 완전히 틀린 구조는 아니지만, 유지보수성과 가독성 관점에서는 개선 여지가 크다.

특히 아래 질문에 바로 답하기 어려운 구조는 개선 가치가 높다.

- 이 경로는 공개인가 비공개인가
- 어디서 로그인 여부를 막는가
- 어디서 본인 자원 여부를 막는가
- 403이 나면 어느 레이어를 봐야 하는가

이 문맥에서 현재 접근 제어 구조를 정리하고 리팩토링하는 것은 충분히 좋은 과제다.
