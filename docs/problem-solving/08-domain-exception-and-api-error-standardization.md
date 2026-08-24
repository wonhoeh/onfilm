# 도메인 예외와 API 오류 응답 표준화

- 작업일: 2026-08-25
- 문서 작성일: 2026-08-25
- 관련 커밋: `b6044cc`, `7372ab2`, `b35a58e`, `b921b7b`, `32683ce`, `5ad5919`, `8602bbc`, `042c89e`, `cda5855`
- 상태: 완료

## 문제

기능이 늘어나면서 같은 종류의 실패가 서로 다른 예외와 응답 방식으로 처리되고 있었다.

- 전용 예외, `IllegalArgumentException`, `IllegalStateException`, `ResponseStatusException`이 혼재
- `GlobalExceptionHandler`가 예외 타입마다 개별 handler를 가지고 HTTP 상태와 메시지를 반복 정의
- 미디어 작업 상태를 예외 메시지 문자열로 비교해 HTTP 응답 결정
- 조회 실패, 인증·인가, 스토리지·업로드 실패가 호출 위치에 따라 다른 상태와 코드로 반환
- 일부 예외 메시지에 ID나 내부 상세 원인이 포함되어 API로 노출될 가능성 존재
- Spring Security 필터는 MVC 예외 처리기를 거치지 않아 본문 없는 401·403 반환
- 클라이언트가 안정적으로 분기할 공통 오류 코드와 응답 계약 부족

이 구조에서는 메시지 문구를 수정하는 것만으로도 예외 매핑이 깨질 수 있고, 새로운 예외를 추가할 때 서비스·컨트롤러·전역 처리기를 함께 수정해야 했다. 보안 필터와 일반 API가 서로 다른 오류 본문을 반환해 프론트엔드도 진입점별 예외 처리가 필요했다.

## 원인

초기에는 예외가 각 기능 내부의 실패 알림 역할만 했고, API에 공개되는 계약이라는 기준이 없었다. 그 결과 예외 클래스, HTTP 상태, 공개 코드와 메시지의 소유권이 여러 계층으로 분산되었다.

또한 Spring MVC와 Spring Security 필터의 처리 경계가 다르다는 점을 공통 오류 설계에 반영하지 않았다. Controller 이후의 예외는 `GlobalExceptionHandler`가 처리할 수 있지만, 인증·인가 필터에서 응답이 끝나면 MVC 예외 처리기는 실행되지 않는다.

## 해결

`ErrorCode`를 API 오류 정책의 단일 기준으로 두고, 예측 가능한 업무 실패는 `DomainException` 계층으로 통합했다.

```text
Service / Entity
    ↓ DomainException(ErrorCode)
GlobalExceptionHandler
    ↓
ErrorResponse { code, message, errors }
```

핵심 변경은 다음과 같다.

- `ErrorCode`: 공개 오류 코드, HTTP 상태, 기본 메시지 정의
- `DomainException`: 모든 예측 가능한 도메인 실패의 공통 기반 타입
- 기존 전용 예외 11개를 `DomainException` 기반으로 전환
- 조회 실패, 인증·인가, 미디어 상태, 스토리지·업로드 실패에 전용 타입 추가
- `ResponseStatusException`과 메시지 문자열 비교 제거
- `GlobalExceptionHandler`의 예외별 handler를 단일 `DomainException` handler로 통합
- API 응답은 예외의 동적 메시지가 아니라 `ErrorCode` 기본 메시지를 사용해 내부 정보 노출 방지
- Bean Validation은 `422 VALIDATION_FAILED`, 일반 불변식 위반은 기존 `400 BAD_REQUEST` 정책 유지
- 알 수 없는 DB 제약 위반과 낙관적 락 충돌은 각각 공통 409 응답으로 유지

보안 필터에는 별도의 공통 작성기를 적용했다.

```text
Spring Security Filter
    ↓ ErrorCode
SecurityErrorResponseWriter
    ↓
ErrorResponse { code, message, errors }
```

- 미인증 API 요청: `401 AUTHENTICATION_REQUIRED`
- 인증된 사용자의 권한 부족: `403 ACCESS_DENIED`
- CSRF 검증 실패: `403 CSRF_VALIDATION_FAILED`
- 내부 Callback HMAC 인증 실패: `401 INTERNAL_CALLBACK_AUTHENTICATION_FAILED`
- HTML 요청의 `/login.html` 리다이렉트 정책은 유지

마지막으로 새 오류를 추가할 때 따를 예외 선택 기준, 상태 코드, 메시지, 보안 로그, 트랜잭션과 테스트 규칙을 [예외 정책과 오류 코드 컨벤션](../convention/exception-and-error-code-convention.md)으로 문서화했다.

## 기술 선택과 트레이드오프

### 선택한 방법

#### 중앙 `ErrorCode`와 전용 `DomainException`

HTTP 상태와 공개 메시지를 `ErrorCode`에 모으고, 서비스와 엔티티는 의미 있는 예외 타입만 발생시키도록 했다. 클라이언트는 변경 가능한 한국어 메시지가 아니라 안정적인 `code`로 분기할 수 있고, 전역 처리기는 예외가 가진 코드만 해석하면 된다.

전용 예외 타입은 테스트와 `catch`에서 문자열 비교 없이 실패 의미를 표현한다. 인증 실패와 소유권 위반, 만료와 상태 충돌처럼 복구 방식이 다른 상황을 타입 수준에서 구분할 수 있다.

#### 안정적인 기본 메시지 사용

API에는 `exception.getMessage()` 대신 `ErrorCode.message()`를 반환한다. 조회 ID, storage key, 토큰 파싱 원인 같은 내부 정보가 실수로 공개되는 것을 방지하고 동일 코드의 응답을 호출 위치와 무관하게 유지한다.

#### 필터 전용 응답 작성기

Spring Security 필터는 `GlobalExceptionHandler`의 적용 범위 밖이므로 `SecurityErrorResponseWriter`가 같은 `ErrorResponse`를 직렬화하도록 했다. MVC와 필터의 실행 구조는 분리된 채 유지하지만 외부 계약은 하나로 통일한다.

#### 단계적 전환

기반 구조, 기존 예외, 조회, 인증·인가, 미디어 상태, 스토리지, 전역 처리기, 테스트, 보안 필터 순으로 작은 커밋을 남겼다. 각 단계에서 실패 범위와 HTTP 계약을 검증할 수 있고 리뷰 시 변경 목적이 분리된다.

### 검토한 대안

#### `ResponseStatusException`을 서비스에서 직접 사용

구현은 빠르지만 도메인·서비스 계층이 HTTP에 의존하고 공개 코드와 메시지가 호출부에 분산된다. 같은 실패를 메시지 consumer나 batch에서 재사용하기 어렵기 때문에 선택하지 않았다.

#### 예외 클래스별 `@ExceptionHandler` 유지

각 예외의 응답을 개별 조정하기 쉽지만 새 예외마다 전역 처리기를 수정해야 한다. 상태와 메시지가 `ErrorCode`에도 존재하면 두 정책원이 생기므로 공통 `DomainException` handler로 통합했다.

#### 하나의 범용 예외에 `ErrorCode`만 전달

클래스 수는 줄지만 메서드 선언과 테스트에서 실패 의미가 잘 드러나지 않고 특정 실패를 타입으로 처리할 수 없다. 클라이언트가 구분할 업무 실패에는 작은 전용 예외를 유지했다.

#### 모든 `IllegalArgumentException`과 `IllegalStateException`을 즉시 전용 예외로 변경

단순한 엔티티 불변식까지 모두 공개 오류 코드로 만들면 코드 수와 유지 비용이 불필요하게 증가한다. API 소비자가 별도로 분기해야 하는 실패만 `DomainException`으로 승격하고, 일반 계약 위반은 400 fallback으로 남겼다.

#### 필터에서 예외를 던져 MVC 처리기 재사용

보안 필터의 실패는 DispatcherServlet 이전에 발생하므로 일반적인 `@RestControllerAdvice` 처리 흐름에 안정적으로 위임할 수 없다. 필터 전용 작성기를 두되 동일한 `ErrorCode`와 `ErrorResponse`를 재사용했다.

### 감수한 비용

- `ErrorCode`와 전용 예외 클래스가 늘어나 파일 수와 초기 탐색 비용이 증가한다.
- 공개한 오류 코드는 클라이언트 계약이므로 이름 변경과 의미 재사용이 어려워진다.
- 동적 예외 메시지를 API에 노출하지 않아 상세 진단 정보는 로그와 내부 관측 도구에서 확인해야 한다.
- MVC와 Security Filter에 응답 변환 진입점이 각각 존재하므로 두 경로의 계약 테스트가 모두 필요하다.
- 일반 `IllegalArgumentException` fallback과 전용 도메인 예외가 함께 존재하므로 승격 기준을 컨벤션과 리뷰에서 지속적으로 지켜야 한다.

## 검증

- `DomainException`의 필수 `ErrorCode`, 기본 메시지와 원인 예외 보존 테스트
- 기존 및 신규 전용 예외의 `ErrorCode` 연결 테스트
- 모든 `ErrorCode`가 HTTP 상태와 비어 있지 않은 기본 메시지를 가지는지 검증
- Spring MVC에서 400, 401, 403, 404, 409, 410, 415 응답 계약 검증
- Bean Validation의 `422 VALIDATION_FAILED`와 필드 오류 응답 검증
- User, Storyboard, Filmography, Media Job, Upload Request의 조회·인증·상태 실패 경로 테스트
- Local/S3 스토리지의 동일한 키 검증 정책 테스트
- CSRF와 내부 Callback HMAC 필터의 JSON 401·403 응답 테스트
- HTML 요청의 로그인 페이지 리다이렉트 동작 유지 확인
- `setStatus(401/403)`, `sendError(401/403)` 잔존 여부 검색 결과 없음
- 최종 전체 Gradle 테스트 238개 통과, 실패 0개
- `git diff --check` 통과

관련 구현 커밋 범위는 81개 파일에서 1,635줄 추가, 220줄 삭제로 확인했다. 이는 성능 지표가 아니라 예외 정책이 인증, 영화, 미디어, 스토리지와 테스트 전반에 걸쳐 적용된 범위를 나타낸다.

## 결과

변경 전에는 예외 타입과 메시지, handler 분기에 따라 같은 실패가 다른 응답으로 반환될 수 있었다. 변경 후에는 예측 가능한 업무 실패가 `DomainException → ErrorCode → ErrorResponse` 흐름을 따르고, HTTP 상태와 공개 메시지의 기준이 한곳에 모였다.

문자열 비교와 내부 상세 메시지 노출을 제거해 문구 변경에 안전해졌으며, 서비스가 HTTP 예외에 의존하지 않게 되었다. MVC와 Security Filter 모두 `{ code, message, errors }` 형식을 반환하므로 클라이언트도 하나의 오류 처리 규칙을 사용할 수 있다. 단계별 커밋과 공통 테스트, 컨벤션 문서가 이후 오류 추가 시 회귀를 막는 기준으로 남았다.

## 후속 과제

- 처리되지 않은 시스템 장애의 공통 500 응답과 로깅·trace ID 정책을 별도로 설계한다.
- DB 제약 이름을 안정적으로 식별해 알 수 있는 중복 위반을 더 구체적인 도메인 코드로 변환한다.
- OpenAPI 또는 별도 오류 카탈로그에 공개 `ErrorCode` 목록과 endpoint별 오류를 연결한다.
- 프론트엔드와 worker에서 메시지가 아닌 오류 코드로만 분기하는지 지속적으로 점검한다.
- 운영 관측성이 필요해지면 코드별 발생량과 401·403 비율을 metric으로 수집한다.

## 포트폴리오 요약 후보

서비스마다 혼재하던 전용 예외, HTTP 예외와 메시지 문자열 분기를 `DomainException–ErrorCode` 구조로 통합하고, 조회·인증·미디어 상태·스토리지 실패를 안정적인 API 코드로 표준화했습니다. Spring MVC 밖에서 동작하는 보안 필터에는 공통 응답 작성기를 적용해 401·403도 동일한 JSON 계약으로 맞췄으며, 전체 테스트 238개와 원시 상태 코드 검색으로 회귀와 누락을 검증했습니다.
