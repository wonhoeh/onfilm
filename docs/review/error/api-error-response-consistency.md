# 주요 API Error Response 일관성 점검

- 점검일: 2026-08-25
- 범위: JSON 클라이언트가 사용하는 `/auth/**`, `/api/**`, `/internal/api/**`
- 기준 구현: `ErrorResponse`, `ErrorCode`, `GlobalExceptionHandler`, `SecurityErrorResponseWriter`
- 관련 커밋: `b6044cc`~`2e8f23a`
- 판정: **주요 업무 실패 경로는 일관적, Spring·Servlet 경계의 일부 오류는 추가 표준화 필요**

## 점검 목적

API 오류가 같은 JSON 구조를 사용하더라도 HTTP 상태, 공개 코드, 메시지와 필드 오류 규칙이 진입점마다 다르면 클라이언트는 별도의 예외 처리를 구현해야 한다. 이번 점검에서는 다음을 확인했다.

- MVC Controller에서 발생한 도메인·검증·프레임워크 예외
- Spring Security 인증·인가 실패
- CSRF와 내부 Worker Callback Filter 실패
- 인증, Person·Storyboard, 미디어 업로드·Job의 주요 오류 코드
- 내부 예외 메시지 노출 여부
- Spring이 직접 처리하는 오류 중 공통 계약에서 빠진 영역

## 공통 오류 계약

JSON API 오류는 다음 구조를 사용한다.

```json
{
  "code": "PERSON_NOT_FOUND",
  "message": "인물 정보를 찾을 수 없습니다.",
  "errors": []
}
```

| 필드 | 계약 |
| --- | --- |
| `code` | 클라이언트가 분기하는 안정적인 `UPPER_SNAKE_CASE` 값 |
| `message` | `ErrorCode`가 소유하는 안전한 공개 메시지 |
| `errors` | 상세 오류가 없으면 빈 배열, Bean Validation 실패면 필드 오류 배열 |

`errors`를 생략하거나 `null`로 반환하지 않는다. 클라이언트는 변경 가능한 한국어 `message`가 아니라 `code`를 기준으로 분기한다.

Bean Validation 실패만 필드별 상세 정보를 포함한다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    {
      "field": "title",
      "message": "스토리보드 제목은 필수입니다."
    }
  ]
}
```

## 오류 변환 경로

### MVC 경로

```text
Controller / Service / Entity
  → DomainException 또는 framework exception
  → GlobalExceptionHandler
  → ErrorCode
  → ErrorResponse { code, message, errors }
```

- 예측 가능한 업무 실패는 `DomainException`이 가진 `ErrorCode`로 변환한다.
- handler는 `exception.getMessage()`를 공개 응답에 사용하지 않는다.
- 예상하지 못한 `RuntimeException`은 stack trace를 서버 로그에 남기고 안전한 500을 반환한다.

### Security·Filter 경로

Spring Security와 Servlet Filter의 실패는 DispatcherServlet 이전에 발생하므로 `GlobalExceptionHandler`가 처리할 수 없다.

```text
Security / CSRF / Internal Callback Filter
  → SecurityErrorResponseWriter
  → ErrorCode
  → application/json; UTF-8
  → ErrorResponse { code, message, errors }
```

MVC와 Filter는 실행 진입점이 다르지만 `ErrorCode`와 `ErrorResponse`를 공유하므로 외부 계약은 동일하다.

## 상태 코드별 일관성

| HTTP | 대표 `ErrorCode` | 주요 발생 상황 | 변환 경로 | 판정 |
| ---: | --- | --- | --- | --- |
| 400 | `BAD_REQUEST` | 잘못된 JSON, 필수 파라미터 누락, 타입 불일치, 일반 계약 위반 | MVC | 일관적 |
| 400 | `INVALID_STORAGE_KEY`, `MEDIA_UPLOAD_REQUEST_MISMATCH`, `MEDIA_SOURCE_FILE_NOT_FOUND`, `MEDIA_OUTPUT_FILE_NOT_FOUND`, `EMPTY_FILE` | 업로드 입력과 파일 계약 위반 | DomainException → MVC | 일관적 |
| 401 | `AUTHENTICATION_REQUIRED` | 보호 API에 인증 정보 없음 | Security Writer | 일관적 |
| 401 | `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` | 로그인·Refresh Token 실패 | DomainException → MVC | 일관적 |
| 401 | `INTERNAL_CALLBACK_AUTHENTICATION_FAILED` | HMAC, timestamp, nonce 검증 실패 | Callback Filter Writer | 일관적 |
| 403 | `ACCESS_DENIED`, `CSRF_VALIDATION_FAILED` | Security 권한·CSRF 실패 | Security Writer | 일관적 |
| 403 | `FORBIDDEN_PERSON_ACCESS`, `FORBIDDEN_MOVIE_ACCESS`, `FORBIDDEN_MEDIA_UPLOAD_ACCESS`, `STORAGE_KEY_NOT_OWNED` | 도메인 소유권 위반 | DomainException → MVC | 일관적 |
| 404 | `USER_NOT_FOUND`, `PERSON_NOT_FOUND`, `MOVIE_NOT_FOUND`, `STORYBOARD_PROJECT_NOT_FOUND`, `STORYBOARD_SCENE_NOT_FOUND` | 주요 Aggregate 조회 실패 | DomainException → MVC | 일관적 |
| 404 | `MEDIA_ENCODE_JOB_NOT_FOUND`, `MEDIA_UPLOAD_REQUEST_NOT_FOUND`, `FILMOGRAPHY_ITEM_NOT_FOUND`, `FILMOGRAPHY_FILE_NOT_FOUND` | 작업·파일 조회 실패 | DomainException → MVC | 일관적 |
| 409 | `DUPLICATE_EMAIL`, `DUPLICATE_USERNAME`, `DATA_INTEGRITY_VIOLATION` | 중복과 DB 무결성 충돌 | DomainException 또는 MVC | 일관적 |
| 409 | `CONCURRENT_MEDIA_JOB_UPDATE`, `INVALID_MEDIA_JOB_STATUS_TRANSITION`, `MEDIA_UPLOAD_ALREADY_COMPLETED`, `PERSON_NOT_LINKED` | 동시성·현재 상태 충돌 | DomainException 또는 MVC | 일관적 |
| 410 | `MEDIA_UPLOAD_REQUEST_EXPIRED` | 만료된 업로드 요청 | DomainException → MVC | 일관적 |
| 413 | `PAYLOAD_TOO_LARGE` | 내부 Callback 본문이 64 KiB 초과 | Callback Filter Writer | 해당 경로 일관적 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 업로드 미디어 형식 | DomainException → MVC | 일관적 |
| 422 | `VALIDATION_FAILED` | Request DTO·method validation 실패 | MVC | 구조 일관적 |
| 500 | `INTERNAL_SERVER_ERROR` | 분류되지 않은 RuntimeException | MVC, server log | 일관적 |
| 503 | `INTERNAL_CALLBACK_UNAVAILABLE` | Callback secret 설정 사용 불가 | Callback Filter Writer | 일관적 |

`MEDIA_SOURCE_FILE_NOT_FOUND`와 `MEDIA_OUTPUT_FILE_NOT_FOUND`는 일반 리소스 조회 API의 404가 아니라 “발급된 업로드·Callback 계약을 완료할 수 없음”을 뜻하므로 현재 정책에서는 400을 사용한다.

## 주요 API 그룹별 점검

### 인증 API `/auth/**`

| 상황 | 응답 |
| --- | --- |
| 회원가입 DTO 오류 | `422 VALIDATION_FAILED`와 필드 `errors` |
| 이메일·username 중복 | `409 DUPLICATE_EMAIL`, `409 DUPLICATE_USERNAME` |
| 로그인 실패 | `401 INVALID_CREDENTIALS` |
| Refresh Token 누락·만료·유효하지 않음 | `401 INVALID_REFRESH_TOKEN` |
| 보호 API 인증 누락 | `401 AUTHENTICATION_REQUIRED` |
| CSRF 실패 | `403 CSRF_VALIDATION_FAILED` |

인증 실패는 이메일 존재 여부나 비밀번호 불일치를 외부에서 구분하지 않고 `INVALID_CREDENTIALS`로 통일해 계정 존재 정보 노출을 줄인다.

### Person·Gallery·Filmography·Storyboard API `/api/people/**`

| 상황 | 응답 |
| --- | --- |
| Person·Project·Scene 부재 | 각 리소스의 404 코드 |
| 현재 사용자와 path `publicId` 불일치 | `403 FORBIDDEN_PERSON_ACCESS` |
| 제목·카드·순서 요청 형식 오류 | `422 VALIDATION_FAILED`와 필드 오류 |
| Filmography 파일 부재 | `404 FILMOGRAPHY_FILE_NOT_FOUND` |
| storage key 형식·소유권 위반 | 400 `INVALID_STORAGE_KEY` 또는 403 `STORAGE_KEY_NOT_OWNED` |

Filmography 파일 조회의 정상 302 redirect는 성공 응답 정책이며, 파일 부재만 공통 JSON 오류 계약을 사용한다.

### Movie·미디어 업로드 API `/api/files/movie/**`, `/api/media-jobs/**`

| 상황 | 응답 |
| --- | --- |
| Movie 부재·편집 권한 부족 | `404 MOVIE_NOT_FOUND`, `403 FORBIDDEN_MOVIE_ACCESS` |
| UploadRequest 부재·소유권 위반 | `404 MEDIA_UPLOAD_REQUEST_NOT_FOUND`, `403 FORBIDDEN_MEDIA_UPLOAD_ACCESS` |
| 발급 정보와 완료 요청 불일치 | `400 MEDIA_UPLOAD_REQUEST_MISMATCH` |
| 원본 파일 부재·빈 파일·형식 오류 | `400 MEDIA_SOURCE_FILE_NOT_FOUND`, `400 EMPTY_FILE`, `415 UNSUPPORTED_MEDIA_TYPE` |
| 업로드 요청 만료·중복 완료 | `410 MEDIA_UPLOAD_REQUEST_EXPIRED`, `409 MEDIA_UPLOAD_ALREADY_COMPLETED` |
| Job 부재·동시 상태 충돌 | `404 MEDIA_ENCODE_JOB_NOT_FOUND`, `409 CONCURRENT_MEDIA_JOB_UPDATE` |

### Worker Callback `/internal/api/media-jobs/**`

| 상황 | 응답 |
| --- | --- |
| HMAC·timestamp·nonce 오류 | `401 INTERNAL_CALLBACK_AUTHENTICATION_FAILED` |
| Callback body 제한 초과 | `413 PAYLOAD_TOO_LARGE` |
| Callback secret 설정 오류 | `503 INTERNAL_CALLBACK_UNAVAILABLE` |
| Job·결과 파일 부재 | `404 MEDIA_ENCODE_JOB_NOT_FOUND`, `400 MEDIA_OUTPUT_FILE_NOT_FOUND` |
| 허용되지 않은 상태 전이 | `409 INVALID_MEDIA_JOB_STATUS_TRANSITION` |

Filter에서 끝나는 401·413·503과 Controller까지 도달한 도메인 실패 모두 같은 JSON 필드를 사용한다.

## 일관성이 확보된 항목

| 점검 항목 | 결과 | 근거 |
| --- | --- | --- |
| JSON 필드 구조 | 통과 | MVC와 Filter 모두 `ErrorResponse` 사용 |
| 상세 오류가 없을 때 `errors` | 통과 | factory가 빈 배열 생성 |
| HTTP 상태·코드·기본 메시지 정책원 | 통과 | `ErrorCode`가 세 값을 함께 소유 |
| DomainException 변환 | 통과 | 단일 `@ExceptionHandler(DomainException.class)` |
| 내부 상세 메시지 미노출 | 통과 | API는 `ErrorCode.message()` 사용 |
| MVC 400·401·403·404·409·410·415 | 통과 | parameterized MockMvc 계약 테스트 |
| Validation 422 필드 오류 | 통과 | Storyboard·Person·Auth Controller 테스트 |
| Security 401·403 JSON | 통과 | 공통 Writer와 인증 통합·CSRF 테스트 |
| Callback 401·413·503 JSON | 통과 | HMAC Filter 계약 테스트 |
| 예상하지 못한 RuntimeException 500 | 통과 | 안전한 메시지와 handler 우선순위 테스트 |
| Controller의 직접 오류 body 생성 | 통과 | `ErrorResponse` 생성은 공통 처리 클래스에만 존재 |
| 원시 `sendError` 사용 | 통과 | API 오류 경로 검색 결과 없음 |

## 범위 밖 또는 추가 표준화가 필요한 항목

### 1. 등록되지 않은 API 경로의 404

`NoResourceFoundException` 또는 Spring Boot 기본 오류 처리 경로를 `GlobalExceptionHandler`가 명시적으로 처리하지 않는다. 따라서 존재하는 리소스를 찾지 못한 도메인 404는 일관적이지만, 존재하지 않는 endpoint 자체의 404는 환경 설정에 따라 공통 `ErrorResponse`와 다른 body가 될 수 있다.

권장 후속 작업:

- `ENDPOINT_NOT_FOUND` 같은 공개 `ErrorCode` 정책 결정
- 실제 Spring context에서 `/api/not-existing` 계약 테스트
- 정적 리소스와 API path의 404 정책 분리

### 2. 지원하지 않는 HTTP method의 405

`HttpRequestMethodNotSupportedException` 전용 처리가 없다. `METHOD_NOT_ALLOWED` 코드를 추가할지, `BAD_REQUEST`로 단순화할지 정책 결정과 통합 테스트가 필요하다.

### 3. 일반 multipart·Servlet 요청 크기 초과

내부 Callback의 64 KiB 제한은 `413 PAYLOAD_TOO_LARGE`로 통일됐지만, 일반 multipart 업로드가 Spring·Servlet 설정의 최대 크기를 넘겼을 때 발생하는 `MaxUploadSizeExceededException`은 명시적으로 처리하지 않는다. 주요 파일 API 전체에서 같은 413 계약을 원하면 별도 handler와 실제 multipart 테스트가 필요하다.

### 4. Method validation 상세 필드

`MethodArgumentNotValidException`은 `errors`에 필드 정보를 담지만 `ConstraintViolationException`은 현재 빈 배열을 반환한다. envelope는 일관적이지만 method parameter 경로까지 상세 정보가 필요한지는 별도 정책으로 정해야 한다.

### 5. HTML 요청과 API 인증 실패 구분

인증 entry point는 `Accept`에 `text/html`이 포함되면 로그인 페이지로 redirect한다. 이는 브라우저 페이지 요청에는 의도된 동작이지만 `/api/**` 요청도 같은 Accept header를 보내면 JSON 401 대신 redirect될 수 있다. 엄격한 API 계약이 필요하면 Accept header만 보지 않고 API path와 page path를 함께 구분해야 한다.

### 6. HTML Controller

`PublicProfilePageController`, `UserPrivatePageController`와 정적 파일은 JSON API 감사 범위가 아니다. `UserPrivatePageController`의 `ResponseStatusException(404)`와 로그인 redirect가 `ErrorResponse`를 사용하지 않는 것은 HTML 화면 응답 정책에 따른 의도적인 예외다.

## 종합 판정

주요 도메인 실패, DTO 검증, 요청 파싱, 인증·인가, CSRF와 내부 Callback은 `ErrorCode → ErrorResponse` 계약으로 일관되게 처리된다. 클라이언트가 실제 업무 흐름에서 분기하는 400·401·403·404·409·410·413·415·422·500·503 경로가 코드와 테스트로 고정돼 있다.

다만 “서버에서 발생 가능한 모든 HTTP 오류”가 완전히 통일된 상태는 아니다. 등록되지 않은 endpoint 404, method 405, 일반 multipart 크기 초과와 HTML Accept 분기는 별도 경계이므로 후속 표준화 전까지는 **주요 API 업무 오류 일관성 완료, 프레임워크 경계 오류 일부 완료**로 표현하는 것이 정확하다.

## 검증 근거

- `GlobalExceptionHandlerMvcTest`: DomainException의 400·401·403·404·409·410·415와 요청 파싱 400, 안전한 500
- `GlobalExceptionHandlerTest`: Validation, DB 무결성, optimistic lock과 메시지 비노출
- `ErrorCodeTest`: 모든 코드의 HTTP 상태와 비어 있지 않은 기본 메시지
- `SecurityErrorResponseWriterTest`: Filter 경계의 JSON·UTF-8와 빈 `errors`
- `AuthIntegrationTest`: 중복 409, Validation 422, 인증 401 계약
- `CsrfProtectionFilterTest`: CSRF 403 계약
- `InternalCallbackHmacFilterTest`: Callback 401·413·503 계약
- `PersonControllerTest`: Storyboard Validation 422와 Filmography 파일 404 계약
- 코드 검색: API 오류 응답의 직접 생성과 원시 `sendError` 잔존 없음
- 마지막 전체 Gradle 테스트 결과: 269개, 실패 0개, 오류 0개, 건너뜀 0개
- `git diff --check` 통과

이번 작업은 문서 감사이며 기존 테스트 결과와 정적 검색을 사용했다. 새로운 성능 수치나 완전한 endpoint 조합 테스트를 수행한 것으로 표현하지 않는다.

## 후속 작업 우선순위

1. 존재하지 않는 `/api/**` 경로의 404를 공통 `ErrorResponse`로 통합
2. 지원하지 않는 method의 405 계약과 `ErrorCode` 정의
3. multipart 크기 초과의 공통 413 처리
4. API path의 인증 실패는 Accept와 무관하게 JSON을 반환하도록 matcher 정교화
5. method validation의 field path를 `errors`에 제공할지 결정

## 면접 설명 예시

OnFilm은 Controller에서 직접 오류 body를 만들지 않고 예측 가능한 실패를 `DomainException`과 `ErrorCode`로 표현합니다. MVC에서는 `GlobalExceptionHandler`, DispatcherServlet 밖의 Security·CSRF·Callback Filter에서는 `SecurityErrorResponseWriter`가 같은 `{ code, message, errors }`를 만들기 때문에 실행 경로가 달라도 클라이언트 계약은 같습니다.

주요 업무 오류와 400·500 fallback은 MockMvc·통합·Filter 테스트로 검증했습니다. 다만 존재하지 않는 endpoint의 404나 405, 일반 multipart 제한처럼 Spring·Servlet이 직접 만드는 경계 오류는 아직 완전히 통일되지 않았다고 구분해 기록했고, 이를 후속 작업으로 남겼습니다.

## 관련 문서

- [예외 정책과 오류 코드 컨벤션](../../convention/exception-and-error-code-convention.md)
- [Domain Validation 위치 결정 가이드](../validation/domain-validation-location-guide.md)
- [도메인 예외와 API 오류 응답 표준화](../../problem-solving/08-domain-exception-and-api-error-standardization.md)

## 유지 규칙

새 `ErrorCode`, Filter 오류 응답, framework exception handler 또는 공개 API 오류 계약을 추가·변경하면 상태 코드 표와 검증 근거를 같은 커밋에서 갱신한다.
