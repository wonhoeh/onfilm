# Infrastructure 클래스 네이밍 컨벤션

## 1. 목적

프로젝트가 커질수록 `Service`라는 이름이 너무 많이 붙으면 역할이 헷갈리기 쉽다.  
특히 비즈니스 서비스와 기술 구현 클래스를 둘 다 `XxxService`로 부르면, 코드를 처음 보는 사람이 책임을 빠르게 구분하기 어렵다.

이 문서는 infrastructure 계층 클래스의 이름을 어떤 기준으로 지을지 정리한 규칙이다.

핵심 원칙은 다음과 같다.

- 비즈니스 흐름은 `Service`
- 기술 구현은 역할별 suffix 사용

즉 infrastructure 클래스는 무조건 `Service`로 통일하지 않고, “이 클래스가 실제로 하는 기술적 역할”을 이름에 드러내는 방향으로 간다.

---

## 2. 가장 중요한 원칙

### 2-1. `Service`는 비즈니스 흐름에 우선 사용한다

`Service`는 보통 아래 책임에 붙인다.

- 비즈니스 로직 흐름
- 도메인 규칙
- 처리 순서 결정
- 상태 변경 orchestration

예:

- `AuthService`
- `PersonReadService`
- `MediaEncodeJobCommandService`

즉 `Service`라는 이름을 보면 먼저 “이 클래스가 업무 흐름을 관리하는구나”라고 이해할 수 있어야 한다.

### 2-2. infrastructure는 역할별 suffix를 쓴다

infrastructure는 기술적 구현이므로, 아래처럼 역할 중심 suffix를 쓴다.

- `Client`
- `Factory`
- `Publisher`
- `Consumer`
- `Storage`
- `Adapter`
- `Provider`
- `Filter`
- `Config`

이렇게 하면 클래스 이름만 보고도 “무슨 기술 역할인지”가 드러난다.

---

## 3. suffix별 사용 기준

## 3-1. `Service`

사용 시점:

- 비즈니스 흐름을 조합할 때
- 여러 인프라/도메인 객체를 묶어 처리할 때

예:

- `AuthService`
- `RefreshTokenService`
- `MovieService`

쓰지 않는 편이 좋은 경우:

- 단순히 쿠키 만드는 클래스
- 단순히 외부 API 호출하는 클래스
- 단순히 S3 업로드만 하는 클래스

---

## 3-2. `Client`

사용 시점:

- 외부 HTTP API 호출
- 외부 시스템 통신

예:

- `CoreApiClient`
- `S3Client` (SDK 수준)

의미:

- “외부 시스템과 통신하는 기술 구현”

---

## 3-3. `Factory`

사용 시점:

- 객체 생성 규칙이 중요할 때
- 응답 객체, 쿠키, 토큰 payload 조립 등

예:

- `AuthCookieFactory`
- `MediaJobMessageFactory`

의미:

- “어떤 규칙에 따라 특정 객체를 만들어내는 역할”

---

## 3-4. `Publisher`

사용 시점:

- Kafka, 이벤트 버스, 메시지 브로커로 발행할 때

예:

- `KafkaMediaJobPublisher`
- `DomainEventPublisher`

의미:

- “메시지를 외부로 내보내는 역할”

---

## 3-5. `Consumer`

사용 시점:

- Kafka, RabbitMQ 등 메시지를 받아 처리할 때

예:

- `EncodingRequestedConsumer`

의미:

- “메시지를 받아서 애플리케이션으로 전달하는 역할”

---

## 3-6. `Storage`

사용 시점:

- 파일 저장
- S3/local file 저장
- key 기반 파일 접근

예:

- `LocalFileStorage`
- `S3FileStorage`

의미:

- “파일 또는 오브젝트 저장소 구현”

비고:

현재 프로젝트의 `LocalStorageService`, `S3StorageService`도 동작상 문제는 없지만, 네이밍만 보면 `Storage` suffix가 더 역할이 선명하다.

---

## 3-7. `Adapter`

사용 시점:

- 외부 기술을 프로젝트 내부 인터페이스에 맞춰 감쌀 때
- 라이브러리/SDK를 내부 모델로 변환할 때

예:

- `S3StorageAdapter`
- `JwtAuthenticationAdapter`

의미:

- “외부 구현을 내부 방식에 맞게 연결하는 역할”

---

## 3-8. `Provider`

사용 시점:

- 값을 제공하거나 계산된 결과를 만들어줄 때
- JWT 생성/파싱처럼 공급자 역할이 있을 때

예:

- `JwtProvider`
- `ClockProvider`

의미:

- “무언가를 제공하는 컴포넌트”

비고:

`JwtProvider`는 현재 네이밍으로도 무난하다.  
다만 더 구체적으로 하고 싶으면 `JwtTokenFactory` 또는 `JwtTokenProvider`로 갈 수도 있다.

---

## 3-9. `Filter`

사용 시점:

- HTTP 요청/응답 전처리
- 인증/보안 필터

예:

- `JwtAuthFilter`
- `CsrfProtectionFilter`

의미:

- “요청 체인 중간에서 가로채는 역할”

---

## 3-10. `Config`

사용 시점:

- Spring 설정
- Bean 등록
- 보안 체인 구성

예:

- `SecurityConfig`
- `WebConfig`

의미:

- “설정/조립 역할”

---

## 4. 추천 규칙 요약

다음 한 줄 규칙으로 정리할 수 있다.

`비즈니스 흐름은 Service, 외부 연동은 Client, 메시지 송신은 Publisher, 메시지 수신은 Consumer, 객체 생성은 Factory, 파일 저장은 Storage, 요청 체인 처리는 Filter, 설정은 Config로 명명한다.`

---

## 5. 현재 프로젝트에 적용해볼 수 있는 예시

### 현재 이름

- `AuthService`
- `LocalStorageService`
- `S3StorageService`
- `JwtProvider`
- `CoreApiClient`

### 더 역할이 선명한 이름 예시

- `AuthService` -> 유지
- `LocalStorageService` -> `LocalFileStorage`
- `S3StorageService` -> `S3FileStorage`
- `JwtProvider` -> 유지 가능 또는 `JwtTokenProvider`
- `CoreApiClient` -> 유지

즉 모든 클래스를 억지로 바꿀 필요는 없고, 새로 만드는 infrastructure 클래스부터 일관되게 가져가는 것이 현실적이다.

---

## 6. AuthCookieFactory는 왜 Factory가 맞는가

`AuthCookieFactory`는 다음 역할을 한다.

- access token 값을 받아 access cookie 생성
- refresh token 값을 받아 refresh cookie 생성
- csrf token 값을 받아 csrf cookie 생성

즉:

- 비즈니스 흐름을 조합하는 것이 아니라
- 특정 규칙에 따라 `ResponseCookie` 객체를 만든다

그래서 `AuthCookieService`보다 `AuthCookieFactory`가 더 적절하다.

---

## 7. 피하면 좋은 패턴

### 7-1. infrastructure에 `XxxService` 남발

예:

- `S3UploadService`
- `CookieService`
- `JwtService`
- `KafkaService`

이런 이름은 처음 봤을 때 비즈니스 서비스인지 기술 구현인지 헷갈리기 쉽다.

### 7-2. `common`에 너무 많이 몰아넣기

인증 전용 기술 구현을 모두 `common`으로 빼면 책임이 흐려진다.

예:

- `AuthCookieFactory`는 `common`보다 `auth.infrastructure.cookie` 쪽이 자연스럽다

---

## 8. 디렉터리 구조 추천

예시:

```text
domain/
  auth/
    controller/
    service/
    infrastructure/
      cookie/
        AuthCookieFactory.java
  file/
    infrastructure/
      local/
        LocalFileStorage.java
      s3/
        S3FileStorage.java
  kafka/
    infrastructure/
      publisher/
      consumer/
```

즉 디렉터리도 역할별로 맞추고, 이름도 역할별 suffix를 맞추는 것이 좋다.

---

## 9. 한 줄 정리

infrastructure 클래스는 `Service`로 통일하지 말고, 그 클래스가 실제로 하는 기술 역할에 따라 `Client`, `Factory`, `Publisher`, `Consumer`, `Storage`, `Filter`, `Config` 같은 suffix를 쓰는 것이 가장 읽기 쉽고 유지보수하기 좋다.
