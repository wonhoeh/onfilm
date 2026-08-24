# Onfilm 예외 정책과 오류 코드 컨벤션

## 1. 목적

Onfilm은 예측 가능한 실패를 `DomainException`과 `ErrorCode`로 표현하고, API 경계에서 공통 `ErrorResponse`로 변환한다.

이 문서의 목적은 다음과 같다.

- 같은 실패가 호출 경로에 따라 다른 HTTP 상태나 응답 형식으로 반환되지 않게 한다.
- 서비스 코드에서 문자열 메시지와 HTTP 상태를 직접 결정하지 않게 한다.
- 클라이언트가 변경 가능한 메시지가 아니라 안정적인 오류 코드로 분기하게 한다.
- 예상 가능한 도메인 실패와 프로그래밍 오류를 구분한다.
- 새로운 예외와 오류 코드를 추가할 때 동일한 기준을 적용한다.

관련 문서:

- [Onfilm 검증 흐름 컨벤션](validation-flow-convention.md)
- [Onfilm DTO 스타일 컨벤션](dto-style-convention.md)
- [Onfilm 엔티티 설계·리팩토링 가이드](entity-refactoring-style-guide.md)

---

## 2. 공통 오류 처리 흐름

```text
Controller / Service / Entity
    ↓ 예외 발생
DomainException
    ↓ ErrorCode 보유
GlobalExceptionHandler
    ↓ HTTP 상태와 응답 변환
ErrorResponse { code, message, errors }
```

Spring Security 필터에서 발생한 401·403은 MVC의 `GlobalExceptionHandler`까지 도달하지 않는다. 따라서 필터에서는 `SecurityErrorResponseWriter`를 사용해 동일한 `ErrorResponse`를 직접 작성한다.

```text
Security Filter
    ↓ 인증·인가 실패
SecurityErrorResponseWriter
    ↓
ErrorResponse { code, message, errors }
```

---

## 3. API 오류 응답 계약

모든 JSON 오류 응답은 다음 형식을 기본으로 한다.

```json
{
  "code": "PERSON_NOT_FOUND",
  "message": "인물 정보를 찾을 수 없습니다.",
  "errors": []
}
```

| 필드 | 용도 | 안정성 |
|---|---|---|
| `code` | 클라이언트 분기와 테스트에 사용하는 기계 판독 값 | 공개 후 의미를 변경하지 않음 |
| `message` | 사용자 또는 개발자가 읽는 기본 설명 | 문구 개선 가능 |
| `errors` | Bean Validation의 필드별 상세 오류 | 상세 검증 오류가 없으면 빈 배열 |

클라이언트는 `message` 문자열을 비교하지 않고 `code`로 분기한다.

```javascript
// 지양
if (response.message === "사용자를 찾을 수 없습니다.") {
    // ...
}

// 권장
if (response.code === "USER_NOT_FOUND") {
    // ...
}
```

---

## 4. 예외 선택 기준

### 4.1 `DomainException`을 사용하는 경우

다음 조건을 만족하는 예측 가능한 업무 실패는 전용 `DomainException`으로 표현한다.

- 클라이언트가 실패 종류를 구분해야 한다.
- HTTP 상태와 공개 오류 코드를 안정적으로 유지해야 한다.
- 여러 Controller 또는 Service에서 같은 의미로 발생할 수 있다.
- 인증, 권한, 존재 여부, 중복, 상태 전이처럼 명확한 도메인 의미가 있다.

```java
Person person = personRepository.findById(personId)
        .orElseThrow(() -> new PersonNotFoundException(personId));
```

```java
if (!movie.isOwnedBy(person)) {
    throw new ForbiddenMovieAccessException();
}
```

### 4.2 `IllegalArgumentException`을 사용하는 경우

메서드에 전달된 값이 해당 객체의 기본 계약이나 불변식을 위반하지만, 클라이언트가 별도의 오류 코드로 분기할 필요가 없을 때 사용한다.

```java
private static String requireTitle(String title) {
    if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("title is required");
    }
    return title.trim();
}
```

현재 `GlobalExceptionHandler`는 이를 `400 BAD_REQUEST`와 `BAD_REQUEST` 코드로 변환한다. API에서 별도로 식별해야 하는 실패가 되면 전용 `DomainException`으로 승격한다.

### 4.3 `IllegalStateException`을 사용하는 경우

입력값 자체가 아니라 현재 객체 상태 때문에 작업을 수행할 수 있고, 그 실패를 별도 API 계약으로 공개할 필요가 없을 때 사용한다.

상태 전이가 클라이언트 또는 worker의 복구·분기 기준이라면 일반 `IllegalStateException` 대신 `InvalidMediaJobStatusTransitionException`처럼 전용 도메인 예외를 사용한다.

### 4.4 Bean Validation 예외

Request DTO의 형식 오류는 직접 예외를 던지지 않고 Bean Validation annotation으로 선언한다.

```java
public record StoryboardProjectRequest(
        @NotBlank(message = "스토리보드 제목은 필수입니다.")
        @Size(max = 120, message = "스토리보드 제목은 120자 이하여야 합니다.")
        String title
) {
}
```

`MethodArgumentNotValidException`과 `ConstraintViolationException`은 `422 Unprocessable Entity`, `VALIDATION_FAILED`로 변환한다.

### 4.5 인프라·영속성 예외

DB나 외부 시스템의 예외를 그대로 API에 노출하지 않는다. 의미를 확정할 수 있는 경계에서 도메인 예외로 변환하고 원인을 `cause`로 보존한다.

```java
try {
    userRepository.saveAndFlush(user);
} catch (DataIntegrityViolationException exception) {
    if (isEmailUniqueViolation(exception)) {
        throw new DuplicateEmailException();
    }
    throw exception;
}
```

제약조건을 특정할 수 없는 `DataIntegrityViolationException`은 전역 처리기가 `409 DATA_INTEGRITY_VIOLATION`으로 변환한다. 특정 이메일·사용자명 중복임을 판별할 수 있다면 각각 `DUPLICATE_EMAIL`, `DUPLICATE_USERNAME`으로 변환한다.

외부 라이브러리 예외를 감쌀 때는 원인 추적이 필요하면 `DomainException(ErrorCode, cause)` 생성자를 사용한다. 단, 원문 예외 메시지나 내부 클래스명은 API 응답에 포함하지 않는다.

### 4.6 예상하지 못한 오류

`NullPointerException`, 매핑 누락, 설정 오류 같은 프로그래밍·시스템 장애를 도메인 예외로 위장하지 않는다. 현재 전역 처리기가 구체적으로 다루지 않는 예외는 Spring의 기본 처리에 맡긴다. 공통 500 응답을 도입하더라도 Controller나 Service에서 개별 변환하지 않고 전역 경계에서 내부 구현을 숨기는 방식으로 처리한다.

새로운 `catch (Exception)`으로 오류를 삼키거나 무조건 `BAD_REQUEST`로 바꾸지 않는다.

---

## 5. `ErrorCode` 작성 규칙

`ErrorCode`는 HTTP 상태와 기본 공개 메시지를 함께 가진다.

```java
PERSON_NOT_FOUND(HttpStatus.NOT_FOUND, "인물 정보를 찾을 수 없습니다.")
```

### 5.1 이름

- 대문자 `UPPER_SNAKE_CASE`를 사용한다.
- 원인이 아니라 클라이언트가 이해할 실패 의미를 표현한다.
- 동일한 의미에는 동일한 코드를 재사용한다.
- 의미가 다른 실패를 포괄적인 코드 하나에 억지로 합치지 않는다.
- 공개된 코드의 이름을 변경하거나 다른 의미로 재사용하지 않는다.
- 보안상 내부 원인을 구분해 공개하면 안 되는 경우에는 여러 내부 예외가 하나의 공개 코드를 사용할 수 있다.

권장 예:

```text
PERSON_NOT_FOUND
FORBIDDEN_MOVIE_ACCESS
INVALID_MEDIA_JOB_STATUS_TRANSITION
MEDIA_UPLOAD_REQUEST_EXPIRED
```

지양 예:

```text
ERROR
FAILED
SERVICE_EXCEPTION
RUNTIME_ERROR
```

### 5.2 HTTP 상태 선택

| 상태 | 사용 기준 | 예시 |
|---|---|---|
| `400 Bad Request` | 형식 검증 이후 발견된 잘못된 값이나 요청 불일치 | `INVALID_STORAGE_KEY` |
| `401 Unauthorized` | 인증 정보가 없거나 유효하지 않음 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| `403 Forbidden` | 인증은 되었지만 대상에 대한 권한이 없음 | `FORBIDDEN_MOVIE_ACCESS`, `ACCESS_DENIED` |
| `404 Not Found` | 요청한 도메인 자원이 없음 | `PERSON_NOT_FOUND` |
| `409 Conflict` | 현재 상태, 중복, 동시성 때문에 요청과 충돌 | `DUPLICATE_EMAIL`, `INVALID_MEDIA_JOB_STATUS_TRANSITION` |
| `410 Gone` | 존재했던 일회성 자원이 만료되어 더 이상 사용할 수 없음 | `MEDIA_UPLOAD_REQUEST_EXPIRED` |
| `415 Unsupported Media Type` | 지원하지 않는 미디어 형식 | `UNSUPPORTED_MEDIA_TYPE` |
| `422 Unprocessable Entity` | Bean Validation 요청 필드 검증 실패 | `VALIDATION_FAILED` |

HTTP 표준 이름의 `Unauthorized`와 달리 401은 “권한 부족”이 아니라 인증 실패에 사용한다. 인증된 사용자의 권한 부족은 403이다.

### 5.3 메시지

- 기본 메시지는 한국어 완결문으로 작성한다.
- 토큰, 비밀번호, 스토리지 키, 서명, 개인정보를 포함하지 않는다.
- 테이블명, SQL, 클래스명 같은 내부 구현을 노출하지 않는다.
- 클라이언트 분기에 필요한 정보를 메시지 문자열에만 넣지 않는다.
- 동일 코드의 기본 메시지는 호출 위치마다 바꾸지 않는다.

현재 `GlobalExceptionHandler`는 `exception.getMessage()`가 아니라 `ErrorCode.message()`를 API에 반환한다. 예외 생성자의 동적 메시지는 내부 진단용일 뿐 공개 응답 계약이 아니다.

---

## 6. 전용 예외 클래스 작성 규칙

전용 예외는 의미 있는 이름과 `ErrorCode`의 연결만 담당하도록 작게 유지한다.

```java
public class MovieNotFoundException extends DomainException {

    public MovieNotFoundException(Long movieId) {
        super(ErrorCode.MOVIE_NOT_FOUND);
    }
}
```

- 클래스 이름은 실패 의미를 나타내고 `Exception`으로 끝낸다.
- 예외가 HTTP, Controller, `ResponseEntity`를 알게 하지 않는다.
- 서비스는 `new DomainException(...)` 같은 익명·범용 예외 대신 전용 타입을 던진다.
- ID를 생성자로 받더라도 공개 메시지에 자동 포함하지 않는다.
- 원인이 있는 변환 예외는 필요한 경우 `Throwable cause` 생성자를 제공한다.
- 복구나 별도 처리가 필요 없는 부가 데이터를 예외 필드로 계속 추가하지 않는다.

전용 타입이 필요 없는 단순 입력·상태 위반은 `IllegalArgumentException` 또는 `IllegalStateException`을 유지할 수 있다. 단지 클래스 수를 늘리기 위해 모든 엔티티 검증을 전용 예외로 만들지 않는다.

---

## 7. 계층별 책임

### Controller

- `@Valid`로 요청 검증을 시작한다.
- 도메인 예외를 `try-catch`하여 응답으로 직접 변환하지 않는다.
- HTTP 응답 형식은 `GlobalExceptionHandler`에 맡긴다.

### Service

- 존재, 권한, 소유권, 중복, 외부 자원 상태를 검사한다.
- 의미 있는 전용 도메인 예외를 발생시킨다.
- 인프라 예외를 해석할 수 있는 경우 도메인 예외로 변환한다.
- HTTP 상태나 `ErrorResponse`를 직접 만들지 않는다.

### Entity와 Value Object

- 생성·변경 불변식과 상태 전이를 보호한다.
- 단순 계약 위반에는 `IllegalArgumentException` 또는 `IllegalStateException`을 사용할 수 있다.
- 업무적으로 구분해야 하는 상태 실패에는 전용 도메인 예외를 사용한다.
- HTTP 계층에 의존하지 않는다.

### Global Exception Handler

- 예외를 HTTP 상태와 `ErrorResponse`로 변환한다.
- `DomainException`은 반드시 보유한 `ErrorCode` 기준으로 처리한다.
- 예외별 `@ExceptionHandler`를 반복 추가하지 않는다.
- 검증 오류처럼 응답 구조가 다른 경우에만 별도 handler를 둔다.

### Security Filter

- 인증·인가 실패에 `sendError()`나 빈 401·403 응답을 직접 사용하지 않는다.
- `SecurityErrorResponseWriter`와 `ErrorCode`로 공통 JSON 응답을 작성한다.
- 브라우저 HTML 요청의 로그인 리다이렉트처럼 명시된 UI 정책은 별도로 유지한다.

---

## 8. 로그와 보안 규칙

예외 응답과 서버 로그의 목적을 구분한다.

- API 응답: 안전하고 안정적인 코드와 기본 메시지
- 서버 로그: 원인 분석에 필요한 문맥과 stack trace
- 감사 로그: 사용자 ID, 요청 ID, 이벤트 종류처럼 추적에 필요한 식별 정보

다음 값은 예외 메시지와 로그에 남기지 않는다.

- 원문 access token과 refresh token
- 토큰 hash
- 비밀번호와 인증 코드
- HMAC secret과 signature
- presigned URL의 민감한 query parameter

```java
// 지양
log.warn("invalid refresh token: {}", rawToken);

// 권장
log.warn("refresh token reuse detected. userId={}", userId);
```

같은 예외를 여러 계층에서 반복 로깅하지 않는다. 처리하거나 추가 문맥을 제공할 수 있는 경계에서 한 번 기록한다.

---

## 9. 트랜잭션과 예외

`DomainException`은 `RuntimeException`이므로 기본적으로 현재 Spring 트랜잭션을 롤백한다.

- 예외 전에 일부 상태만 변경하지 않도록 전체 조건을 먼저 검증한다.
- 실패와 함께 반드시 보존해야 하는 감사 기록은 별도 컴포넌트의 `REQUIRES_NEW` 트랜잭션을 검토한다.
- 외부 파일 삭제처럼 DB rollback으로 되돌릴 수 없는 작업은 커밋 이후 이벤트에서 수행한다.
- 예외를 잡고 정상 반환하면 트랜잭션이 커밋될 수 있으므로 의도 없이 예외를 삼키지 않는다.

---

## 10. 새 오류 추가 절차

1. 기존 `ErrorCode`에 같은 의미의 코드가 있는지 확인한다.
2. 클라이언트가 별도로 분기해야 하는 실패인지 판단한다.
3. 의미에 맞는 HTTP 상태와 민감정보 없는 기본 메시지를 정한다.
4. `ErrorCode`에 새 상수를 추가한다.
5. `DomainException`을 상속한 전용 예외를 추가한다.
6. Service 또는 Entity에서 문자열 비교 대신 새 예외 타입을 사용한다.
7. `GlobalExceptionHandler`에 전용 handler를 추가하지 않고 공통 handler 동작을 사용한다.
8. 예외와 응답 계약 테스트를 추가한다.
9. 프론트엔드나 worker가 사용하는 공개 코드라면 API 문서와 소비자 명세도 갱신한다.

```java
public class ExampleNotFoundException extends DomainException {

    public ExampleNotFoundException(Long id) {
        super(ErrorCode.EXAMPLE_NOT_FOUND);
    }
}
```

---

## 11. 테스트 규칙

새 예외 또는 오류 코드를 추가할 때 다음 범위를 검증한다.

- `ErrorCode`에 HTTP 상태와 비어 있지 않은 기본 메시지가 있는가?
- 전용 예외가 의도한 `ErrorCode`를 보유하는가?
- MVC 응답의 status, `code`, `message`, 빈 `errors`가 일치하는가?
- Bean Validation 실패는 `VALIDATION_FAILED`와 필드 오류를 반환하는가?
- 보안 필터의 401·403도 동일한 JSON 구조를 반환하는가?
- 민감정보가 응답이나 로그에 포함되지 않는가?

메시지만 비교하지 말고 공개 계약인 상태와 코드를 우선 검증한다.

```java
assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PERSON_NOT_FOUND);
```

```java
mockMvc.perform(get("/api/example/1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EXAMPLE_NOT_FOUND"))
        .andExpect(jsonPath("$.errors").isEmpty());
```

---

## 12. 피해야 할 패턴

```java
// 문자열을 비교해 오류 종류 판단
if (exception.getMessage().equals("job not found")) {
    // ...
}
```

```java
// Service가 HTTP 응답을 직접 결정
return ResponseEntity.status(404)
        .body(Map.of("error", "not found"));
```

```java
// Controller마다 같은 예외를 개별 처리
try {
    service.execute();
} catch (PersonNotFoundException exception) {
    return ResponseEntity.notFound().build();
}
```

```java
// 보안 필터가 본문 없는 상태 코드만 반환
response.setStatus(403);
```

```java
// 예상하지 못한 장애를 사용자 오류로 위장
catch (Exception exception) {
    throw new IllegalArgumentException("요청 오류");
}
```

---

## 13. 리뷰 체크리스트

- [ ] 클라이언트가 구분해야 하는 실패에 전용 `DomainException`을 사용했는가?
- [ ] 기존 코드와 의미가 겹치는 새 `ErrorCode`를 만들지 않았는가?
- [ ] 401과 403을 인증·인가 의미에 맞게 구분했는가?
- [ ] Service와 Entity가 HTTP 타입에 의존하지 않는가?
- [ ] 문자열 메시지 비교로 예외를 판별하지 않는가?
- [ ] `GlobalExceptionHandler`의 공통 `DomainException` 처리를 재사용하는가?
- [ ] 보안 필터도 공통 `ErrorResponse` 형식을 사용하는가?
- [ ] 내부 예외와 민감정보가 응답 또는 로그에 노출되지 않는가?
- [ ] 예외 발생 시 트랜잭션 롤백과 감사 기록 보존 정책을 검토했는가?
- [ ] 상태, 오류 코드, 응답 구조를 검증하는 테스트가 있는가?
