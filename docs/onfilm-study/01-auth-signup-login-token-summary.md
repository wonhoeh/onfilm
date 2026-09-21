# 1. 회원가입·로그인·토큰 관리 — 요약·검토본

작성일: 2026-09-17

이 파일은 설명을 통합·요약하고 코드 검토 내용을 반영한 보조 문서입니다. 사용자가 제공한 학습 원문은 [회원가입·로그인·토큰 관리](./01-auth-signup-login-token.md)에 별도로 보존합니다.

회원가입부터 로그인, 요청 인증, 토큰 재발급, 로그아웃까지 실제 OnFilm 코드를 따라 공부한 기록입니다. 대화 중 겹친 번호는 회원가입을 `1-1`, 로그인·토큰 기본 동작을 `1-2` 아래로 묶었습니다. 이후 학습한 `1-3`부터 `1-9`까지의 번호는 유지합니다.

코드는 해당 동작에 집중하도록 일부 필드·애너테이션을 생략한 발췌 또는 설명용 예시입니다. 실제 구현을 확인할 수 있도록 마지막에 소스 링크를 모았습니다. 이 문서는 학습 기록이며, 테스트 실행 결과 보고서는 아닙니다.

## 목차

- 1-1. 회원가입
  - 1-1-1. 회원가입 요청의 전체 흐름
  - 1-1-2. 회원가입 입력값 검증
  - 1-1-3. UserEmail과 Username 값 객체
  - 1-1-4. 비밀번호를 안전하게 저장하는 방법
  - 1-1-5. 이메일·사용자 이름 중복 검사
  - 1-1-6. User와 기본 Person 생성, Cascade와 저장
- 1-2. 로그인과 토큰 기본 동작
  - 1-2-1. 로그인 전체 흐름
  - 1-2-2. Access Token과 JWT 인증 흐름
  - 1-2-3. Access Token과 Refresh Token을 나눈 이유
  - 1-2-4. Refresh Token Rotation
  - 1-2-5. 폐기된 Refresh Token 재사용 탐지
  - 1-2-6. Refresh Token 동시 요청과 낙관적 락
  - 1-2-7. 만료된 Refresh Token 처리
  - 1-2-8. 로그아웃 처리
  - 1-2-9. 쿠키·HTTP 세션과 쿠키 보안 옵션
- 1-3. 쿠키 인증과 CSRF 방어
- 1-4. 인증과 인가, 프로필 소유권 검사
- 1-5. 프론트엔드의 자동 재발급과 요청 재시도
- 1-6. 동시에 발생한 토큰 재발급 요청을 하나로 모으기
- 1-7. 만료된 Refresh Token의 사용 차단과 DB 정리
- 1-8. 토큰 관리 테스트 읽기
- 1-9. 로그인부터 로그아웃까지 전체 흐름과 현재 구현의 한계

## 1-1. 회원가입

### 1-1-1. 회원가입 요청의 전체 흐름

사용자는 이메일, 비밀번호, 사용자 이름을 입력해 회원가입합니다.

```http
POST /auth/signup
Content-Type: application/json

{
  "email": "Test3@OnFilm.local",
  "password": "testPassword123!",
  "username": "test3"
}
```

```text
회원가입 요청
→ SignupRequest 입력값 검사
→ 이메일과 사용자 이름 정리
→ 비밀번호 규칙 검사
→ 이메일·사용자 이름 중복 확인
→ DB 트랜잭션 밖에서 BCrypt 해싱
→ 저장 트랜잭션에서 중복 재확인
→ User와 기본 Person 생성
→ 두 객체 저장·커밋
→ HTTP 201 Created
```

Controller는 요청을 받고 Service를 호출합니다.

```java
@PostMapping("/signup")
public ResponseEntity<Void> signup(
        @Valid @RequestBody SignupRequest request
) {
    authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
}
```

- 클래스의 기본 주소가 `/auth`이므로 전체 주소는 `POST /auth/signup`입니다.
- `@RequestBody`는 요청 JSON을 `SignupRequest`로 변환합니다.
- `@Valid`는 Controller 메서드 본문을 실행하기 전에 DTO의 검증 규칙을 검사합니다.
- 성공 응답은 `201 Created`이며 본문은 없습니다.

`AuthService.signup()`은 회원가입의 실행 순서를 정합니다.

```java
public void signup(SignupRequest request) {
    UserEmail email = UserEmail.from(request.email());
    Username username = Username.from(request.username());
    String rawPassword = RawPasswordPolicy.validate(request.password());

    transactionService.validateSignupAvailability(email, username);
    String encodedPassword = passwordEncoder.encode(rawPassword);
    transactionService.register(email, encodedPassword, username);
}
```

이메일은 소문자로 정리하고, 사용자 이름은 표시용 값과 중복 비교용 값을 만듭니다. 중복이 없으면 비밀번호를 해싱하고 저장을 요청합니다.

```text
POST /auth/signup
→ JSON 변환·DTO 검증
→ AuthController.signup()
→ AuthService.signup()
   ├─ UserEmail.from()
   ├─ Username.from()
   ├─ RawPasswordPolicy.validate()
   ├─ validateSignupAvailability()
   └─ PasswordEncoder.encode()
→ AuthTransactionService.register()
   ├─ 중복 재확인
   ├─ User.create()
   ├─ user.createPerson()
   └─ userRepository.saveAndFlush()
→ 트랜잭션 커밋
→ HTTP 201
```

회원가입 성공이 자동 로그인을 의미하지는 않습니다. 가입 후 별도로 로그인 요청을 보냅니다.

### 1-1-2. 회원가입 입력값 검증

회원가입은 HTTP 요청의 입구와 서비스 내부에서 각각 값을 검사합니다.

```text
SignupRequest → HTTP 요청의 입력 형식 검사
UserEmail·Username·RawPasswordPolicy → 내부 코드에서도 값의 규칙 유지
```

요청 DTO는 다음과 같습니다.

```java
public record SignupRequest(
    @NotBlank
    @Email
    @Size(max = UserEmail.MAX_LENGTH)
    String email,

    @NotBlank
    @ValidRawPassword
    String password,

    @NotBlank
    @Size(min = Username.MIN_LENGTH, max = Username.MAX_LENGTH)
    @Pattern(regexp = Username.PATTERN_VALUE)
    String username
) {
}
```

| 검증 | 의미 | 예시 |
|---|---|---|
| `@NotBlank` | null, 빈 문자열, 공백만 있는 문자열 거부 | `null`, `""`, `"   "` 거부 |
| `@Email` | 이메일 형식 검사 | `user@example.com` 허용, `not-an-email` 거부 |
| `@Size` | 문자열 길이 검사 | 이메일 최대 254자, 사용자 이름 3~20자 |
| `@Pattern` | 지정한 정규식과 일치하는지 검사 | 사용자 이름의 영문·숫자·`_`·`-` 허용 |
| `@ValidRawPassword` | 프로젝트의 비밀번호 정책 검사 | 최소 8자, UTF-8 최대 72바이트 |

사용자 이름의 정규식은 다음과 같습니다.

```text
^[a-zA-Z0-9_-]{3,20}$

^             → 시작
[a-zA-Z0-9_-] → 영문자·숫자·밑줄·하이픈
{3,20}        → 3~20자
$             → 끝
```

`test_user-1`은 허용하지만 `test user`, `테스트`, `test!`는 거부합니다.

DTO 검증에서 실패하면 Service를 호출하지 않고 `422`와 필드별 오류를 반환합니다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    { "field": "email", "message": "올바른 형식의 이메일 주소여야 합니다." }
  ]
}
```

#### 사용자 정의 검증: @ValidRawPassword

기본 애너테이션만으로는 OnFilm의 비밀번호 길이·바이트 규칙을 모두 표현하기 어려워 전용 검증기를 사용합니다.

```java
@Constraint(validatedBy = RawPasswordValidator.class)
public @interface ValidRawPassword {
    // message, groups, payload 등의 선언 생략
}
```

검증기의 핵심은 다음과 같습니다.

```java
public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
        return true;
    }
    try {
        RawPasswordPolicy.validate(value);
        return true;
    } catch (IllegalArgumentException exception) {
        return false;
    }
}
```

`null`에서 true를 반환하는 이유는 필수값 검사를 함께 붙은 `@NotBlank`에 맡겼기 때문입니다. 실제 정책은 null 이외에도 공백 등을 검사할 수 있으므로, 모든 잘못된 입력에서 검증 메시지가 반드시 하나만 생긴다는 뜻은 아닙니다.

#### DTO를 통과했는데 왜 다시 검사할까?

HTTP 요청은 DTO 검증을 거치지만 테스트, 초기 데이터 생성, 다른 Service에서의 직접 호출은 거치지 않을 수 있습니다.

```java
UserEmail.from("잘못된 이메일");
```

이 경우에도 `UserEmail`이 값을 거부합니다. DTO는 요청 입구에서 필드 오류를 안내하고, 값 객체와 정책 클래스는 내부에서도 잘못된 값이 사용되지 않게 합니다.

`UserEmail.from("  User@Example.COM  ")`은 직접 호출하면 공백을 제거합니다. 다만 HTTP 요청에서는 앞선 `@Email` 검사가 공백 포함 이메일을 먼저 거부할 수 있습니다. 사용자 이름도 DTO의 정규식 검사가 정규화보다 먼저 실행됩니다. 따라서 값 객체의 정규화 예시를 곧바로 HTTP 허용 입력이라고 보면 안 됩니다.

### 1-1-3. UserEmail과 Username 값 객체

이메일과 사용자 이름은 요청에서는 String이지만 내부에서는 의미 있는 전용 타입으로 바꿉니다.

```java
UserEmail email = UserEmail.from(request.email());
Username username = Username.from(request.username());
```

모두 String이면 인자 순서를 바꿔도 컴파일러가 알아차리지 못할 수 있습니다.

```java
// 설명용 예시
register(username, email, password);
```

전용 타입을 사용하면 이메일 자리에는 `UserEmail`, 사용자 이름 자리에는 `Username`이 필요하므로 잘못된 전달을 줄일 수 있습니다.

#### UserEmail 구조

```java
public final class UserEmail {
    private final String value;

    private UserEmail(String value) {
        this.value = value;
    }

    public static UserEmail from(String rawEmail) {
        // 필수값 검사 → 정규화 → 길이·형식 검사
        // 검사 후 new UserEmail(normalized) 반환
    }

    public String value() {
        return value;
    }
}
```

위 `from()` 본문은 설명용으로 생략했습니다. 실제 정규화는 다음과 같습니다.

```java
String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
```

`trim()`은 앞뒤 공백을 제거하고 `Locale.ROOT`를 이용한 소문자 변환은 실행 환경의 언어 설정에 따른 차이를 줄입니다.

```text
"  User@Example.COM  " → "user@example.com"
```

생성자가 private이므로 외부에서는 `new UserEmail(...)`을 사용할 수 없습니다. `from()`을 통해 필수값·길이·형식을 검사해야 객체를 만들 수 있습니다.

이처럼 생성자 대신 객체를 만들어 반환하는 메서드를 정적 팩토리 메서드라고 합니다.

#### final의 의미

| 위치 | 의미 |
|---|---|
| `final` 필드 | 초기화한 필드에 다른 값을 다시 대입하지 못함 |
| `final` 메서드 | 자식 클래스가 메서드를 재정의하지 못함 |
| `final` 클래스 | 해당 클래스를 상속하지 못함 |

`UserEmail`은 final 클래스이므로 자식 클래스가 `value()` 등을 재정의해 검증되지 않은 값을 반환하는 구조를 만들 수 없습니다. 현재는 생성자도 private이라 외부 상속이 제한되며, final은 상속용 클래스가 아니라는 의도를 명확히 합니다.

`value`는 private final이고 String 자체도 불변이므로, 생성 후 이메일 값을 바꾸려면 새로운 `UserEmail`을 만들어야 합니다.

#### Username의 표시값과 비교값

```java
private final String value;
private final String normalized;
```

```text
Username.from("  Test_User  ")
value      = "Test_User"
normalized = "test_user"
```

화면에서는 사용자가 정한 대소문자를 유지하고, 중복 검사에서는 대소문자를 무시합니다.

```text
Test_User / test_user / TEST_USER
→ 모두 normalized 값은 test_user
```

DB에는 `username`과 `username_normalized`를 저장하고 후자에 Unique Constraint를 적용합니다.

#### User Entity와의 차이

`User`는 `@Entity`와 `@Id`를 가진 DB 저장 대상입니다. `UserEmail`과 `Username`은 별도 Entity나 테이블이 아니며, 검증된 문자열을 전달하는 데 사용됩니다.

```java
this.email = email.value();
this.username = username.value();
this.usernameNormalized = username.normalized();
```

현재 두 클래스에는 값 기반 `equals()`·`hashCode()` 구현도 없습니다. 여기서 값 객체라는 표현은 검증·정규화·불변 값 전달 역할을 가리키며, 서로 다른 인스턴스를 `equals()`로 비교하면 내부 문자열이 같아도 같다고 판정하지 않습니다.

### 1-1-4. 비밀번호를 안전하게 저장하는 방법

비밀번호 원문을 DB에 저장하면 DB 유출 시 비밀번호도 그대로 노출됩니다. OnFilm은 BCrypt 해시를 저장합니다.

```java
String encodedPassword = passwordEncoder.encode(rawPassword);
```

실제 구현체는 Spring Bean으로 등록한 `BCryptPasswordEncoder`입니다.

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

`AuthService`는 `PasswordEncoder` 인터페이스로 주입받습니다.

```text
입력 비밀번호 → BCrypt 해싱 → 해시 문자열 → DB 저장
```

#### 암호화와 해싱

암호화는 키를 이용한 복호화를 전제로 하지만, 비밀번호 해싱에는 저장값을 원문으로 복호화하는 기능이 없습니다. 다만 공격자가 후보 비밀번호를 대입해 일치 여부를 알아낼 수는 있으므로, 해싱했다고 추측 공격까지 불가능해지는 것은 아닙니다.

BCrypt는 무작위 Salt를 사용하고 계산에 비용을 들여 대량 추측을 어렵게 합니다. 같은 비밀번호를 두 번 해싱해도 Salt가 다르면 결과도 달라집니다.

```java
passwordEncoder.encode("password123!");
passwordEncoder.encode("password123!");
// 보통 서로 다른 BCrypt 문자열이 만들어짐
```

Salt는 비밀번호와 함께 해싱에 사용하는 무작위 값입니다. BCrypt 저장 문자열에는 버전, 계산 비용, Salt와 해시 결과가 포함되므로 Salt 전용 컬럼이 필요하지 않습니다.

```text
$2a$10$...
 │   └─ 계산 비용 설정
 └───── BCrypt 버전 표기
```

#### 로그인에서는 어떻게 비교할까?

```java
passwordEncoder.matches(입력한원문, DB의비밀번호해시);
```

`matches()`는 저장 문자열의 Salt와 비용 정보를 이용해 입력 비밀번호를 검증합니다. 새 Salt로 `encode()`한 두 문자열을 직접 비교하는 방식이 아닙니다.

```text
입력 비밀번호
→ 저장된 BCrypt 문자열의 Salt·비용으로 계산
→ 저장된 결과와 비교
→ 일치하면 true
```

#### 비밀번호 정책과 원문 취급

현재 정책은 최소 8자, UTF-8 기준 최대 72바이트입니다. BCrypt 입력 한계를 고려해 글자 수와 바이트 수를 구분합니다.

```java
rawPassword.getBytes(StandardCharsets.UTF_8).length
```

일반적인 영문 한 글자는 1바이트이고 한글 음절은 3바이트이므로, 한글 25자는 보통 75바이트가 되어 거부됩니다.

원문은 회원가입 시 해싱하고 로그인 시 비교하는 데 사용합니다. 비밀번호나 비밀번호를 포함한 요청 DTO 전체를 로그로 출력하면 안 됩니다.

### 1-1-5. 이메일·사용자 이름 중복 검사

OnFilm은 애플리케이션 조회와 DB Unique Constraint를 함께 사용합니다.

```java
private void validateAvailability(UserEmail email, Username username) {
    if (userRepository.existsByEmail(email.value())) {
        throw new DuplicateEmailException();
    }
    if (userRepository.existsByUsernameNormalized(username.normalized())) {
        throw new DuplicateUsernameException();
    }
}
```

중복이면 `409 Conflict`와 `DUPLICATE_EMAIL` 또는 `DUPLICATE_USERNAME` 오류를 반환합니다.

#### 애플리케이션에서 두 번 조회하는 이유

```text
첫 번째 조회 트랜잭션
→ 중복 확인
→ 트랜잭션 종료

BCrypt 해싱
→ DB 트랜잭션 밖에서 CPU 계산

두 번째 저장 트랜잭션
→ 중복 재확인
→ User·Person 저장
→ 커밋
```

첫 번째 검사는 이미 가입된 값에 대해 불필요한 BCrypt 계산을 피합니다. 해싱 중 DB 연결을 오래 점유하지 않도록 조회 트랜잭션을 먼저 끝냅니다.

두 번째 검사는 해싱하는 동안 다른 요청이 같은 이메일을 저장했는지 다시 확인합니다.

```text
요청 A: 중복 없음 확인 → BCrypt 계산 중
요청 B: 같은 이메일로 가입·커밋 완료
요청 A: 해싱 완료 → 저장 트랜잭션에서 다시 조회 → 중복 발견
```

#### 두 번 조회해도 DB 제약조건이 필요한 이유

두 번째 검사 직후에도 요청들이 겹칠 수 있습니다.

```text
요청 A: 두 번째 조회 → 없음
요청 B: 두 번째 조회 → 없음
요청 A: 저장·커밋 성공
요청 B: 같은 값 저장 → Unique Constraint 위반
```

조회와 저장 사이에 다른 요청이 끼어들 수 있는 경쟁 상황입니다. 최종 중복 방지는 DB가 담당합니다.

```java
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(
            name = "uk_users_username_normalized",
            columnNames = "username_normalized"
        )
    }
)
```

매핑은 제약조건의 의도를 표현합니다. 운영 스키마는 Flyway 마이그레이션으로 관리하므로 실제 DB에도 해당 제약조건이 존재해야 합니다.

| 방어 단계 | 역할 |
|---|---|
| 첫 조회 | 중복이면 비싼 해싱을 생략 |
| 저장 직전 조회 | 첫 조회 이후 달라진 DB 상태 확인 |
| DB Unique Constraint | 동시 요청까지 포함해 중복 저장 차단 |

#### saveAndFlush와 예외 변환

```java
try {
    userRepository.saveAndFlush(user);
} catch (DataIntegrityViolationException exception) {
    throw translateDuplicateUserException(exception);
}
```

`saveAndFlush()`는 저장 후 flush를 수행해 DB 제약조건 오류를 현재 try-catch 안에서 확인하도록 합니다.

```text
uk_users_email 위반 → DuplicateEmailException
uk_users_username_normalized 위반 → DuplicateUsernameException
그 밖의 무결성 오류 → 원래 예외 유지
```

일반적으로 SQL이 늦게 실행되면 메서드 본문이 끝난 뒤 커밋 과정에서 예외가 발생해 내부 catch로 잡기 어려울 수 있습니다. 다만 현재 User는 IDENTITY 방식이라 `save()` 도중 ID 확보를 위해 INSERT가 즉시 실행될 수도 있습니다. `save()`는 항상 INSERT를 미룬다고 외우면 안 됩니다.

`saveAndFlush()`도 커밋은 아닙니다. 이후 예외가 발생하면 트랜잭션 전체를 롤백할 수 있습니다. 모든 저장에 강제 flush가 필요한 것은 아니며, 현재 위치에서 DB 오류를 확인해야 할 때 사용합니다.

### 1-1-6. User와 기본 Person 생성, Cascade와 저장

회원가입은 계정과 기본 프로필을 함께 만듭니다.

```java
User user = User.create(email, encodedPassword, username);
user.createPerson(username.value());
```

```text
User: 이메일·비밀번호 해시·사용자 이름
Person: 이름·publicId·프로필 정보
User 한 명 ↔ Person 하나
```

#### 생성 제한과 require

`User.create()`는 private 생성자를 호출합니다.

```java
private User(UserEmail email, String encodedPassword, Username username) {
    UserEmail requiredEmail = require(email, "email");
    Username requiredUsername = require(username, "username");
    this.email = requiredEmail.value();
    this.encodedPassword = requireEncodedPassword(encodedPassword);
    this.username = requiredUsername.value();
    this.usernameNormalized = requiredUsername.normalized();
}
```

`require`는 Java 기본 문법이 아니라 이 클래스가 정의한 보조 메서드입니다. 이름은 “필수로 요구한다”는 의미입니다.

```java
private static <T> T require(T value, String fieldName) {
    if (value == null) {
        throw new IllegalArgumentException(fieldName + " is required");
    }
    return value;
}
```

null이면 예외를 발생시키고, 아니면 같은 객체를 반환합니다. `<T>`는 입력 타입을 반환 타입에도 유지하는 제네릭 표기입니다. 해시 문자열의 형식까지 이 메서드가 검증하는 것은 아닙니다. 실제 비밀번호 해싱은 AuthService가 수행합니다.

#### 기본 Person의 값

```java
public Person createPerson(String name) {
    return createPerson(name, null, null, null, null, List.of(), List.of());
}
```

```text
name            = 사용자 이름
birthDate       = null
birthPlace      = null
oneLineIntro    = null
profileImageKey = null
snsList         = 빈 목록
profileTags     = 빈 목록
publicId        = 새 UUID
```

가입 후 프로필 편집으로 나머지 정보를 채웁니다. 이미 Person이 있으면 두 번째 생성을 거부합니다.

```java
if (this.person != null) {
    throw new IllegalStateException("user already has a person");
}
```

`attachPerson()`은 User와 Person 양쪽의 참조를 연결하고, 다른 계정이나 다른 프로필과의 잘못된 재연결을 막습니다.

```text
user.getPerson() → Person
person.getUser() → User
```

#### JPA 설정의 의미

```java
@OneToOne(
    fetch = FetchType.LAZY,
    optional = false,
    cascade = CascadeType.ALL,
    orphanRemoval = true
)
@JoinColumn(name = "person_id", nullable = false, unique = true)
private Person person;
```

| 설정 | 의미 |
|---|---|
| `@OneToOne` | 계정과 프로필의 일대일 관계 |
| `optional = false` | 저장되는 User에는 Person이 필요하다는 JPA 관계 선언 |
| `nullable = false` | DB의 person_id가 NULL이면 안 됨 |
| `unique = true` | 같은 Person을 여러 User가 참조하지 못함 |
| `cascade = ALL` | persist·merge·remove·refresh·detach 작업을 연결된 Person에 전파 |
| `orphanRemoval = true` | 관리되는 관계에서 제거된 자식의 삭제를 처리 |

Cascade는 내부에서 반드시 `personRepository.save()`를 호출한다는 뜻이 아닙니다. JPA가 연관 Entity에도 영속성 작업을 전파합니다. 임의의 업무 메서드 호출까지 전파하는 설정은 아니며, DB의 ON DELETE CASCADE와도 다릅니다.

User를 삭제할 때 자식 삭제를 전파하는 REMOVE와 관계에서 빠진 자식을 삭제하는 orphanRemoval도 구분합니다. 현재 도메인 메서드는 임의의 Person 교체나 null 연결을 허용하지 않습니다.

```java
User user = User.create(...); // 생성 직후 잠시 person이 null일 수 있음
user.createPerson(...);      // 저장 전에 연결
userRepository.saveAndFlush(user);
```

`createPerson()`을 빠뜨리면 저장·flush 과정에서 관계 필수 조건이나 DB NOT NULL 조건에 걸릴 수 있습니다. Spring의 `DataIntegrityViolationException`으로 전달될 수 있고, Hibernate의 `PropertyValueException` 등이 원인일 수 있습니다. 정확한 예외 경로는 실행 환경과 검증 시점에 따라 달라집니다.

#### 함께 저장하고 함께 롤백

users가 person_id를 참조하므로 개념적인 저장 순서는 다음과 같습니다.

```text
Person INSERT → 생성된 ID 확인 → User INSERT(person_id 포함)
→ 모두 성공하면 커밋
```

Person 저장 뒤 User 저장이 중복 오류로 실패하면 같은 트랜잭션의 Person INSERT도 취소됩니다. 계정 없이 기본 프로필만 남는 상황을 막습니다.

## 1-2. 로그인과 토큰 기본 동작

### 1-2-1. 로그인 전체 흐름

```http
POST /auth/login
Content-Type: application/json

{
  "email": "test3@onfilm.local",
  "password": "testPassword123!"
}
```

```text
LoginRequest 검증
→ 이메일 정규화
→ 이메일로 User 조회
→ BCrypt로 비밀번호 비교
→ Access Token 생성
→ Refresh Token 생성·해시 저장
→ Access·Refresh·CSRF 쿠키 생성
→ HTTP 200
```

로그인 DTO는 이메일의 필수값·형식·길이를 검사하고, 비밀번호에는 `@NotBlank`를 적용합니다. 가입 정책을 다시 적용하기보다 저장된 해시와의 일치 여부를 확인합니다.

```java
public AuthTokens login(LoginRequest request) {
    UserEmail email = UserEmail.from(request.email());
    AuthTransactionService.LoginSnapshot user =
            transactionService.findLoginSnapshot(email);

    if (!passwordEncoder.matches(request.password(), user.encodedPassword())) {
        throw invalidCredentials();
    }

    String accessToken = jwtProvider.createAccessToken(
            user.userId(), authProperties.accessTokenTtl());
    String refreshToken = refreshTokenService.issue(
            user.userId(), authProperties.refreshTokenTtl());
    return new AuthTokens(accessToken, refreshToken);
}
```

조회 트랜잭션은 필요한 값을 작은 객체로 반환하고 끝납니다.

```java
@Transactional(readOnly = true)
public LoginSnapshot findLoginSnapshot(UserEmail email) {
    User user = userRepository.findByEmail(email.value())
            .orElseThrow(InvalidCredentialsException::new);
    return new LoginSnapshot(user.getId(), user.getEncodedPassword());
}

public record LoginSnapshot(Long userId, String encodedPassword) {}
```

로그인 검증에 필요한 사용자 ID와 비밀번호 해시만 전달하며, BCrypt 비교는 이 조회 트랜잭션 밖에서 수행합니다.

이메일이 없을 때와 비밀번호가 틀렸을 때 모두 같은 오류를 반환합니다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "errors": []
}
```

HTTP 상태는 401입니다. 이 로그인 오류 메시지를 통해 계정 존재 여부가 바로 드러나지 않게 한 것입니다. 서비스 전체에서 가입 여부를 절대 알 수 없다는 보장은 아닙니다.

`AuthTokens`는 Service에서 Controller로 두 토큰을 전달하는 내부 DTO입니다.

```java
public record AuthTokens(String accessToken, String refreshToken) {}
```

Controller는 AuthCookieFactory로 세 쿠키를 만들고 `Set-Cookie`로 전달합니다.

```http
Set-Cookie: access_token=...
Set-Cookie: refresh_token=...
Set-Cookie: XSRF-TOKEN=...
```

실제 헤더에는 Path·Max-Age 등의 옵션도 붙습니다. 현재 응답 본문은 `new AuthResponse(null)`이므로 다음 형태입니다.

```json
{ "accessToken": null }
```

### 1-2-2. Access Token과 JWT 인증 흐름

OnFilm의 Access Token은 서명된 JWT입니다.

```text
Header.Payload.Signature
```

| 부분 | 역할 |
|---|---|
| Header | 서명 알고리즘 등의 정보 |
| Payload | 사용자 ID·발급 시각·만료 시각 |
| Signature | 비밀키로 서명한 내용이 변조되지 않았는지 확인 |

생성 코드의 핵심입니다.

```java
return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
```

- `subject` / `sub`: 사용자 ID
- `issuedAt` / `iat`: 발급 시각
- `expiration` / `exp`: 만료 시각
- `signWith`: 비밀키로 서명
- `compact`: 전송할 문자열 생성

현재 JWT의 Payload는 암호화되어 있지 않아 토큰을 가진 사람이 읽을 수 있습니다. 비밀번호·카드 번호 같은 비밀을 넣으면 안 됩니다. 서명은 내용을 숨기기보다 변경 여부와 발급 신뢰성을 확인합니다.

```text
sub = 10인 토큰의 Payload를 sub = 1로 변경
→ 기존 서명과 불일치
→ 검증 실패
```

#### JwtAuthFilter에서 토큰 꺼내기

```java
String auth = request.getHeader("Authorization");
String token = null;
if (auth != null && auth.startsWith("Bearer ")) {
    token = auth.substring(7);
} else if (request.getCookies() != null) {
    // access_token 쿠키 검색
}
```

Bearer 형식 헤더를 우선하고, 그런 헤더가 없으면 쿠키를 찾습니다. Bearer 토큰이 잘못됐다고 쿠키로 다시 인증하는 흐름은 아닙니다.

```http
Authorization: Bearer <JWT>
```

또는:

```http
Cookie: access_token=<JWT>
```

`OncePerRequestFilter`를 바탕으로 일반 요청의 필터 처리를 수행합니다. 비동기·오류 재디스패치의 동작은 필터 설정에 따라 달라질 수 있습니다.

#### 검증과 Authentication 등록

```java
if (token != null && !token.isBlank() && jwtProvider.validate(token)) {
    Authentication authentication = jwtProvider.getAuthentication(token);
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
filterChain.doFilter(request, response);
```

JWT 라이브러리가 형식·서명·만료 등을 확인합니다. 통과하면 subject에서 userId를 꺼내 Spring Security의 인증 객체를 만듭니다.

```text
SecurityContext
└─ Authentication
   └─ principal의 이름 = userId 문자열
```

여기서 principal 생성에 사용하는 Spring Security의 User 클래스와 DB Entity인 OnFilm의 User는 서로 다른 클래스입니다.

필터는 인증 정보를 설정하고 다음 단계로 넘깁니다. 유효한 토큰이 없으면 인증 정보를 설정하지 않으며, 공개 API는 허용될 수 있지만 보호된 API는 인증 검사에서 거부됩니다. 다른 필터가 먼저 거부하면 응답은 달라질 수 있습니다. 예를 들어 CSRF 검증 실패는 403입니다.

#### Stateless의 의미

```java
.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

서버의 HttpSession에 로그인 상태를 저장하지 않고 요청마다 토큰으로 인증합니다. 현재 요청의 인증 정보를 SecurityContext에 잠시 보관하는 것과 모순되지 않습니다.

JWT 인증 자체에 세션 DB 조회는 필요하지 않지만, 이후 프로필 수정 등 업무 처리에서는 userId로 User를 DB 조회할 수 있습니다.

### 1-2-3. Access Token과 Refresh Token을 나눈 이유

기본 설정은 다음과 같습니다.

```yaml
access-token-ttl: 15m
refresh-token-ttl: 14d
```

| 구분 | Access Token | Refresh Token |
|---|---|---|
| 용도 | 일반 API 인증 | 인증 토큰 재발급 |
| 형태 | 서명된 JWT | 64바이트 난수에서 만든 문자열 |
| 기본 수명 | 15분 | 14일 |
| DB 저장 | 하지 않음 | 해시·발급 및 폐기 상태 저장 |
| 브라우저 저장 | HttpOnly 쿠키 | HttpOnly 쿠키 |
| 서버의 개별 폐기 | 현재 즉시 차단 구조 없음 | DB에서 가능 |

Access Token만 오래 사용하면 탈취 시 API를 사용할 수 있는 시간도 길어집니다. 그래서 수명을 짧게 두고, 로그인 유지에는 별도 관리 가능한 Refresh Token을 사용합니다. Refresh Token만으로 일반 API를 인증하지 않습니다.

#### Refresh Token 생성과 저장

```java
byte[] random = new byte[64];
SecureRandomHolder.INSTANCE.nextBytes(random);
return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
```

Refresh Token 자체에는 userId가 들어 있지 않습니다. 서버가 원문을 SHA-256으로 해싱한 뒤 DB에서 같은 해시의 발급 기록을 찾습니다.

```text
발급: 난수 원문 생성 → 해시를 DB 저장 → 원문을 쿠키 전달
검증: 쿠키 원문 수신 → 같은 방식으로 해싱 → DB의 token_hash 조회
```

DB에는 다음 필드가 있습니다.

| 필드 | 의미 |
|---|---|
| id | 토큰 기록의 ID |
| user_id | 소유 사용자 |
| token_hash | 원문의 SHA-256 해시 |
| created_at | 발급 시각 |
| expires_at | 만료 시각 |
| revoked_at | 폐기 시각, 미폐기이면 null |
| last_used_at | 마지막 사용 또는 폐기 처리에서 기록한 시각 |
| version | 동시 변경 충돌 검사 |

비밀번호는 추측하기 쉬울 수 있어 느린 BCrypt를 사용합니다. Refresh Token은 서버가 충분히 긴 난수로 만들기 때문에 현재는 SHA-256으로 원문 노출을 줄입니다. DB 해시값 자체를 원문 대신 보내면 서버가 다시 해싱하므로 정상 토큰으로 조회되지 않습니다.

### 1-2-4. Refresh Token Rotation

Rotation은 재발급할 때마다 기존 Refresh Token을 폐기하고 새로운 것으로 바꾸는 방식입니다.

```text
Token A로 재발급 → A 폐기 → Token B 발급
이후에는 Token B로 재발급
```

Controller는 쿠키에서 토큰을 받습니다.

```java
@PostMapping("/refresh")
public ResponseEntity<AuthResponse> refresh(
    @CookieValue(name = "refresh_token", required = false) String refreshToken
) {
    // null·공백이면 InvalidRefreshTokenException
    // authService.refresh() 호출 후 새 쿠키 응답
}
```

`required = false`는 쿠키가 없어도 Controller 호출을 허용한다는 뜻입니다. 쿠키가 없으면 null이 들어오며, 재발급 기능 자체가 토큰 없이 허용되는 것은 아닙니다. Controller가 직접 401 오류로 바꿉니다.

Service는 다음 순서로 처리합니다.

```text
원문과 TTL 검사
→ 원문 SHA-256 해싱
→ DB에서 token_hash 조회
→ 폐기 여부 검사
→ 만료 여부 검사
→ 기존 토큰 consume()
→ saveAndFlush()로 변경·version 충돌 확인
→ 새 난수 토큰 생성
→ 새 토큰 해시 저장
→ 사용자 ID와 새 원문 반환
```

TTL은 양수이고 최대 90일 이하여야 하며 기본값은 14일입니다.

`consume()`은 사용과 폐기를 함께 기록합니다.

```java
this.lastUsedAt = usedAt;
this.revokedAt = usedAt;
```

새 토큰은 재발급 시각으로부터 TTL을 계산합니다.

```java
RefreshToken newToken = RefreshToken.issue(
    existing.getUserId(),
    tokenHashing.sha256(newRawToken),
    now,
    now.plus(requiredTtl)
);
refreshTokenRepository.save(newToken);
```

```text
교체 전: A.revokedAt = null
교체 후: A.revokedAt = 현재 시각
         B.revokedAt = null, B.expiresAt = 현재 + 14일
```

`AuthService.refresh()`는 반환된 userId로 새 Access Token을 만들고, Controller는 Access·Refresh·CSRF 쿠키를 다시 발급합니다. 같은 이름·도메인·Path의 쿠키가 교체됩니다.

#### 같은 트랜잭션으로 묶는 이유

```text
A만 폐기되고 B 저장 실패 → 사용할 Refresh Token이 없어짐
B만 저장되고 A 폐기 실패 → 둘 다 사용 가능
```

따라서 DB의 A 폐기와 B 저장을 함께 커밋하거나 롤백합니다. 다만 DB 커밋과 브라우저의 응답 수신까지 하나의 DB 트랜잭션으로 묶이는 것은 아닙니다. 커밋 후 응답을 받지 못해 A로 다시 요청하면 재사용으로 처리될 수 있습니다.

새 Refresh Token은 매번 현재 시각 기준 14일이므로, 정상적으로 재발급을 계속하면 로그인 유지 기간도 연장될 수 있습니다. 최초 로그인에서 무조건 14일 뒤 종료하는 절대 만료 정책과는 다릅니다.

### 1-2-5. 폐기된 Refresh Token 재사용 탐지

```java
if (existing.isRevoked()) {
    Long userId = existing.getUserId();
    securityTransactionService.deleteAllSessionsForReuse(userId);
    throw new RefreshTokenReuseDetectedException();
}
```

`isRevoked()`는 `revokedAt != null`인지 확인합니다. 이미 사용·폐기한 A가 다시 들어오면 탈취 가능성이 있다고 보고 해당 사용자의 Refresh Token을 모두 삭제합니다.

```text
기존 A 기록 삭제
새로 발급한 B 기록 삭제
다른 기기의 C 기록도 삭제
```

여기서 sessions라는 이름은 Refresh Token으로 유지하는 로그인 기록을 뜻합니다. 서버의 HttpSession을 삭제한다는 의미가 아닙니다. 기존 Access Token은 남은 유효기간 동안 인증에 사용할 수 있으므로 모든 기기의 요청이 그 순간 즉시 차단되는 것은 아닙니다.

#### REQUIRES_NEW가 필요한 이유

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void deleteAllSessionsForReuse(Long userId) {
    refreshTokenRepository.deleteAllByUserId(userId);
    refreshTokenRepository.flush();
}
```

같은 트랜잭션에서 삭제한 뒤 RuntimeException을 던지면 삭제까지 롤백됩니다. 별도 서비스의 새 트랜잭션에서 삭제를 확정합니다.

```text
rotate 트랜잭션 시작
→ 폐기된 토큰 발견
→ 기존 트랜잭션 잠시 중단
→ 새 트랜잭션에서 해당 사용자의 토큰 삭제·커밋
→ 기존 트랜잭션 복귀
→ 재사용 예외 발생·롤백
→ 새 트랜잭션에서 확정한 삭제는 유지
```

외부에는 `401 INVALID_REFRESH_TOKEN`을 반환하고, 서버에는 사용자 ID를 이용한 재사용 경고 로그를 남깁니다. 원문 토큰은 로그에 남기지 않습니다.

서버는 탈취와 정상적인 중복·재시도를 완전히 구분하지 못합니다. 따라서 이 정책은 안전을 우선하는 대신 정상 사용자도 여러 기기에서 재로그인해야 할 수 있습니다.

### 1-2-6. Refresh Token 동시 요청과 낙관적 락

같은 토큰으로 두 요청이 거의 동시에 재발급을 시도할 수 있습니다. 여러 탭, 중복 요청, 네트워크 재시도 등이 원인입니다.

RefreshToken의 version을 이용해 중복 변경을 막습니다.

```java
@Version
@Column(nullable = false)
private Long version;
```

두 요청이 모두 version 0을 읽었다고 가정합니다.

```sql
-- 개념적인 UPDATE
UPDATE refresh_tokens
SET revoked_at = ?, last_used_at = ?, version = 1
WHERE id = 100 AND version = 0;
```

먼저 커밋한 요청이 version을 1로 바꾸면, 나중 요청의 version 0 조건에 맞는 행이 없습니다. Hibernate가 수정 행 수를 보고 충돌로 판단합니다.

```java
try {
    existing.consume(now);
    refreshTokenRepository.saveAndFlush(existing);
} catch (OptimisticLockingFailureException exception) {
    throw new InvalidRefreshTokenException(exception);
}
```

먼저 처리한 요청은 새 토큰을 발급하고, 충돌한 요청은 새 토큰 생성 전에 중단돼 401로 처리됩니다. flush를 이 위치에서 수행해야 현재 catch에서 충돌을 변환할 수 있습니다.

낙관적 락은 조회할 때부터 명시적으로 행을 잠그기보다 변경할 때 version을 비교하는 방식입니다. 실제 UPDATE 단계에는 DB의 잠금 처리가 있을 수 있으므로 DB 락이 전혀 없다는 뜻은 아닙니다.

일반 Entity 수정에서는 JPA가 version을 증가시킵니다. 다만 뒤에서 보는 로그아웃의 JPQL 일괄 UPDATE는 version을 직접 증가시키도록 작성돼 있습니다.

두 요청이 항상 낙관적 락 충돌로 끝나는 것은 아닙니다. 후속 요청이 첫 요청의 커밋 후 조회하면 이미 폐기된 토큰을 보게 되며, 이때는 재사용 탐지와 전체 토큰 삭제 경로로 갑니다.

### 1-2-7. 만료된 Refresh Token 처리

현재 시각이 만료 시각과 같거나 이후이면 만료입니다.

```java
public boolean isExpiredAt(Instant instant) {
    Instant requiredInstant = require(instant, "instant");
    return !requiredInstant.isBefore(expiresAt);
}
```

```text
현재 < 만료 → 유효
현재 >= 만료 → 만료
```

재발급에서는 만료 토큰 사용을 기록하고 거부합니다.

```java
if (existing.isExpiredAt(now)) {
    securityTransactionService.recordExpiredUse(existing.getId(), now);
    throw new InvalidRefreshTokenException();
}
```

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordExpiredUse(Long tokenId, Instant usedAt) {
    RefreshToken token = refreshTokenRepository.findById(tokenId)
            .orElseThrow(InvalidRefreshTokenException::new);
    token.revoke(usedAt);
    refreshTokenRepository.saveAndFlush(token);
}
```

새 트랜잭션에서 토큰을 다시 조회해 `lastUsedAt`과 `revokedAt`을 기록하고 커밋합니다. 이후 바깥 재발급 트랜잭션이 예외로 롤백돼도 기록이 남습니다.

Access Token 만료는 JWT의 exp로 검사하고 별도 DB 기록을 남기지 않습니다. Refresh Token은 DB의 expiresAt을 검사하고 사용 시도까지 기록합니다.

현재는 폐기 검사가 만료 검사보다 먼저입니다. 만료 사용을 한 번 기록해 폐기된 토큰이 다시 들어오면 다음 요청은 재사용 탐지 경로로 갈 수 있습니다.

### 1-2-8. 로그아웃 처리

로그아웃은 현재 Refresh Token의 DB 폐기와 브라우저 쿠키 삭제로 구성됩니다.

현재 Controller는 `POST /auth/logout`과 `GET /auth/logout`을 모두 허용합니다. 프론트엔드는 POST를 사용합니다.

```java
@CookieValue(name = "refresh_token", required = false)
String refreshToken
```

쿠키가 없으면 null을 받아도 됩니다. 로그아웃에서는 원문이 null·공백이면 DB 처리를 생략하고 쿠키 삭제 응답을 만듭니다. 존재하지 않거나 이미 폐기한 토큰도 오류 없이 처리하므로 반복 로그아웃에 대해 멱등적입니다.

```java
public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
        return;
    }
    refreshTokenRepository.revokeActiveByTokenHash(
        tokenHashing.sha256(rawToken), clock.instant());
}
```

Repository는 Entity를 하나씩 수정하기보다 JPQL 일괄 UPDATE를 수행합니다.

```sql
update RefreshToken rt
set rt.lastUsedAt = :revokedAt,
    rt.revokedAt = :revokedAt,
    rt.version = rt.version + 1
where rt.tokenHash = :tokenHash
  and rt.revokedAt is null
```

이미 폐기됐거나 존재하지 않으면 수정 행 수가 0이어도 정상 종료합니다. 여기서 active는 미폐기 조건이며, 쿼리가 expiresAt까지 검사하는 것은 아닙니다.

쿠키 삭제는 빈 값과 `Max-Age=0`으로 응답합니다.

```http
Set-Cookie: access_token=; Path=/; Max-Age=0
Set-Cookie: refresh_token=; Path=/auth; Max-Age=0
Set-Cookie: XSRF-TOKEN=; Path=/; Max-Age=0
```

생성할 때의 이름·도메인·Path와 일치해야 기존 쿠키를 삭제할 수 있습니다. 실제 응답에는 다른 옵션도 포함됩니다. 성공 상태는 본문 없는 `204 No Content`입니다.

#### Access Token까지 즉시 무효화되는가?

브라우저 쿠키는 지워지지만, 외부에 복사된 JWT는 현재 구조에서 남은 유효기간 동안 사용할 수 있습니다. Refresh Token 폐기는 미래의 재발급을 막습니다.

즉시 Access Token을 차단하려면 차단 목록이나 사용자 토큰 버전 확인 같은 추가 상태 검사가 필요합니다. 현재 구현된 기능과 구분해야 합니다.

GET 로그아웃은 조회 요청으로 상태를 바꾸는 구조라 링크 이동·미리 가져오기 등으로 의도치 않게 실행될 수 있습니다. POST로 제한하고 CSRF 정책을 함께 정리하는 것은 보완할 항목입니다.

### 1-2-9. 쿠키·HTTP 세션과 쿠키 보안 옵션

쿠키는 브라우저가 저장하며 이름과 값 외에 도메인·Path·유효기간 같은 속성이 있습니다.

```text
access_token=JWT문자열
```

HTTP 세션은 서버가 사용자별 상태를 보관하고 브라우저의 세션 ID로 그 상태를 찾는 방식입니다. Servlet 환경에서는 HttpSession과 JSESSIONID가 대표적입니다.

| 표현 | 문맥 |
|---|---|
| HTTP 세션 | 서버의 HttpSession 상태 |
| 로그인 세션 | 로그인 상태가 이어지는 기간·기록을 뜻하는 일반 표현 |
| DB 세션 | DB 연결의 상태·작업 문맥 |
| SSH 세션 | 원격 서버와 연결된 접속 문맥 |

OnFilm의 STATELESS는 HttpSession으로 인증 상태를 유지하지 않는다는 뜻입니다. Refresh Token을 DB에 저장하는 것은 가능하며, 이를 로그인 세션 기록이라고 표현하기도 합니다.

#### HttpOnly

```java
.httpOnly(true)
```

JavaScript의 `document.cookie`에서 해당 쿠키를 읽지 못하게 합니다. 브라우저는 조건에 맞는 요청에는 여전히 쿠키를 전송합니다. XSS가 토큰을 직접 읽는 위험을 줄이지만 악성 스크립트의 모든 요청을 막지는 않습니다.

Access·Refresh는 true이고, CSRF 쿠키는 JavaScript가 읽어 헤더에 넣어야 하므로 false입니다. 실제 헤더 이름은 `X-CSRF-TOKEN`입니다.

#### Secure

```java
.secure(authProperties.accessCookieSecure())
```

true이면 원칙적으로 HTTPS에서 전송합니다. HTTPS 운영에서는 true로 설정해야 평문 전송을 제한할 수 있습니다.

2026-09-17 소스 확인 기준으로 `application-prod.yml`의 세 쿠키 Secure 값은 모두 false이며 k6 HTTP 테스트용이라는 주석이 있습니다. 따라서 운영에서 이미 true로 적용됐다고 단정하지 않습니다. 실제 실행 환경의 설정 덮어쓰기와 응답 헤더는 별도 확인 대상입니다.

#### SameSite

다른 사이트에서 시작한 요청에 쿠키를 보낼 범위를 제한합니다. 사이트와 출처는 같은 개념이 아니며, 서로 다른 서브도메인이 같은 사이트로 취급될 수 있습니다.

| 값 | 일반적인 동작 |
|---|---|
| Strict | 교차 사이트 요청에서 쿠키 전송을 강하게 제한 |
| Lax | 일반적인 교차 사이트 POST 등은 제한하고 최상위 GET 이동 등 일부 허용 |
| None | 교차 사이트 전송 허용, Secure 필요 |

OnFilm 기본값은 Lax입니다. SameSite만으로 모든 CSRF를 해결했다고 보면 안 됩니다.

#### Path와 Max-Age

Path는 전송할 URL 경로 범위를 정합니다. Refresh Token의 `/auth`는 refresh·logout뿐 아니라 같은 경로 범위의 다른 요청에도 적용되며, 별도의 접근 권한 검사는 아닙니다.

Max-Age는 브라우저 보관 시간을 초로 지정합니다. 15분은 900초, 14일은 1,209,600초이며 0은 삭제입니다. 쿠키 만료와 서버에서 토큰 만료를 검사하는 것은 별개의 동작입니다.

| 옵션 | access_token | refresh_token | XSRF-TOKEN |
|---|---|---|---|
| 값 | JWT | 무작위 토큰 원문 | 무작위 CSRF 값 |
| HttpOnly | true | true | false |
| Secure | 설정값 적용 | 설정값 적용 | 설정값 적용 |
| Path 기본값 | `/` | `/auth` | `/` |
| SameSite 기본값 | Lax | Lax | Lax |
| Max-Age | Access TTL | Refresh TTL | Access TTL |

## 1-3. 쿠키 인증과 CSRF 방어

CSRF는 브라우저가 인증 쿠키를 자동 전송하는 성질을 이용해 사용자가 원하지 않은 요청을 보내게 하는 공격입니다.

```text
사용자가 서비스에 로그인
→ 다른 사이트 방문
→ 그 사이트가 서비스로 요청을 보내도록 유도
→ 쿠키 전송 조건이 맞으면 인증 쿠키가 함께 전송
→ 서버가 사용자 의도의 요청으로 오인할 위험
```

실제 가능 여부는 HTTP 메서드, SameSite, 브라우저의 CORS 정책 등에 따라 달라집니다. 예를 들어 외부 사이트가 임의의 DELETE와 헤더를 제한 없이 보낼 수 있다는 뜻은 아닙니다. 단순 폼 POST처럼 쿠키가 실린 요청이 서버에 도달하는 상황을 이해하는 것이 핵심입니다.

JWT도 쿠키로 자동 전송하면 CSRF 검토가 필요합니다. Authorization 헤더에 명시적으로 붙이는 토큰과 전달 방식이 다릅니다.

### 쿠키와 헤더 비교

로그인·재발급 때 서버가 `XSRF-TOKEN` 쿠키를 발급하고 프론트엔드가 그 값을 읽어 헤더에 넣습니다.

```javascript
const csrf = getCsrfToken();
if (csrf) {
    headers.set("X-CSRF-TOKEN", csrf);
}
```

```http
Cookie: access_token=...; XSRF-TOKEN=abc123
X-CSRF-TOKEN: abc123
```

서버는 둘 다 존재하고 비어 있지 않으며 같은지 확인합니다.

```java
if (csrfCookieValue == null || csrfCookieValue.isBlank()
        || csrfHeader == null || csrfHeader.isBlank()) {
    reject(response);
    return;
}
if (!csrfCookieValue.equals(csrfHeader)) {
    reject(response);
    return;
}
filterChain.doFilter(request, response);
```

같은 값을 쿠키와 헤더로 보내는 Double Submit Cookie 방식입니다. 다른 출처의 페이지는 일반적으로 OnFilm의 쿠키 값을 읽지 못하고, 임의의 사용자 정의 헤더를 보내는 데에도 브라우저 정책의 제약을 받습니다.

다만 이 검사가 OnFilm 화면에서 실행된 코드라는 사실을 절대적으로 증명하는 것은 아닙니다. XSS로 같은 출처에서 악성 코드가 실행되면 CSRF 값을 읽을 수 있습니다. 현재는 로그인 세션에 결합된 서명형 CSRF 토큰이 아니라 단순 일치 비교입니다.

### 요청 출처 확인

```java
String origin = request.getHeader("Origin");
String referer = request.getHeader("Referer");
String host = request.getHeader("Host");

if (!isSameOrigin(origin, host) && !isSameOrigin(referer, host)) {
    reject(response);
    return;
}
```

- Host: 요청을 받는 호스트 정보
- Origin: 요청을 시작한 출처
- Referer: 요청을 유발한 페이지 주소 정보. 브라우저 정책에 따라 일부만 있거나 없을 수 있음

현재 코드는 Origin 또는 Referer 중 하나라도 비교를 통과하면 진행합니다. 함수 이름은 `isSameOrigin`이지만 스킴과 포트를 엄격하게 비교하는 완전한 출처 검증은 아닙니다. 이 한계는 1-9에서 다시 정리합니다.

### 검사 대상과 제외 경로

GET·HEAD·OPTIONS·TRACE는 SAFE_METHODS에 있어 검사를 건너뜁니다. 따라서 GET으로 상태를 바꾸는 구현은 별도로 주의해야 합니다.

그 외 메서드는 경로 예외가 아니면 출처와 CSRF 값을 검사합니다. 실패하면 `403 CSRF_VALIDATION_FAILED`와 공통 오류 JSON을 반환합니다.

현재 경로 제외는 다음 문자열로 시작하는지 확인하는 방식입니다.

```java
return path.startsWith("/auth/login")
        || path.startsWith("/auth/signup")
        || path.startsWith("/auth/refresh")
        || path.startsWith("/auth/logout")
        || path.startsWith("/internal/api/");
```

이는 정확히 일치하는 URL 검사보다 넓은 범위입니다. 로그인 전 CSRF 쿠키가 없을 수 있다는 점만으로 인증 API의 제외가 모두 안전하다고 결론 내리면 안 됩니다. 로그인·refresh·logout의 CSRF 위험과 제외 범위는 추가 검토 대상입니다.

Worker Callback은 사용자 브라우저의 쿠키 인증을 사용하지 않으므로 CSRF 검사에서 제외하고 HMAC 서명과 MEDIA_WORKER 권한으로 보호합니다. 내부 API를 인증 없이 허용하는 것과 다릅니다.

### Spring 기본 CSRF 기능과의 관계

```java
.csrf(AbstractHttpConfigurer::disable)
```

Spring 기본 CSRF 기능은 끄고 프로젝트의 `CsrfProtectionFilter`를 등록합니다. 따라서 위 한 줄만 보고 CSRF 검사가 없다고 설명하면 안 됩니다.

```text
상태 변경 요청
→ 제외 대상인지 확인
→ 요청 출처 비교
→ XSRF-TOKEN 쿠키와 X-CSRF-TOKEN 헤더 비교
→ 통과하면 다음 보안 처리
```

## 1-4. 인증과 인가, 프로필 소유권 검사

| 구분 | 질문 | 예시 |
|---|---|---|
| 인증 | 누구의 요청인가? | 철수의 유효한 토큰인가? |
| 인가 | 이 작업을 할 수 있는가? | 철수가 영희의 프로필을 수정해도 되는가? |

`JwtAuthFilter`가 유효한 토큰에서 사용자 ID를 확인해 SecurityContext에 등록합니다. SecurityConfig는 주소별 접근 규칙을 적용합니다.

```java
.requestMatchers(HttpMethod.GET, "/api/people/*").permitAll()
.requestMatchers("/api/people/**").authenticated()
```

규칙은 처음 일치하는 항목을 적용하므로 공개 조회 규칙을 앞에 둡니다. `authenticated()`는 인증 여부만 확인하며 프로필 주인인지까지 보장하지 않습니다.

프로필 수정 Service는 먼저 소유권을 확인합니다.

```java
Person person = currentPersonProvider.getRequired(publicId);
```

```java
public Person getRequired() {
    Long userId = SecurityUtil.currentUserId();
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    Person person = user.getPerson();
    if (person == null) {
        throw new PersonNotLinkedException();
    }
    return person;
}

public Person getRequired(String publicId) {
    Person person = getRequired();
    if (!Objects.equals(person.getPublicId(), publicId)) {
        throw new ForbiddenPersonAccessException();
    }
    return person;
}
```

`Objects.equals()`는 null을 안전하게 다루며 두 값을 비교합니다.

```text
철수의 토큰으로 요청
→ 철수의 User와 Person 조회
→ 철수의 publicId와 요청 URL의 publicId 비교
→ 영희의 publicId라면 다르므로 403
→ 프로필 변경은 실행하지 않음
```

publicId는 공유 가능한 주소 식별값이며 수정 권한을 증명하는 비밀값이 아닙니다. 반드시 서버가 인증 정보에서 얻은 소유자와 비교해야 합니다.

## 1-5. 프론트엔드의 자동 재발급과 요청 재시도

서버는 Access Token 만료 순간에 브라우저에 먼저 로그아웃을 알리지 않습니다. 다음 요청의 인증 실패를 프론트엔드가 처리해야 합니다.

`auth.js`의 자동 재발급 함수는 다음과 같습니다.

```javascript
async function apiFetchWithAutoRefresh(url, options = {}) {
    if (url === AUTH.refresh) {
        return apiFetch(url, options);
    }

    let res = await apiFetch(url, options);
    if (res.status !== 401 && res.status !== 403) {
        return res;
    }

    const token = await refreshAccessToken();
    if (!token) {
        return res;
    }
    return apiFetch(url, options);
}
```

- url: 요청 주소
- options: HTTP 메서드·본문 등 요청 설정
- res: 서버 응답
- await: 비동기 결과가 준비될 때까지 현재 함수의 다음 처리를 기다림

`apiFetch()`는 CSRF 헤더를 준비하고 다음 설정으로 요청합니다.

```javascript
return fetch(url, {
    ...options,
    headers,
    credentials: "include",
});
```

브라우저가 쿠키 전송 조건에 맞는 인증 쿠키를 함께 보냅니다. include가 SameSite 등 다른 정책을 무시하는 것은 아닙니다.

### 재발급과 원래 요청 재시도

```text
프로필 수정 요청
→ 401 또는 403 응답
→ POST /auth/refresh
→ 브라우저가 Refresh Token 쿠키 전송
→ 서버 Rotation·새 쿠키 발급
→ 브라우저 쿠키 교체
→ 원래 주소와 데이터로 요청 재시도
```

HttpOnly Refresh Token은 JavaScript가 직접 읽지 않습니다. 브라우저가 쿠키로 보냅니다.

현재 `refreshAccessToken()`은 성공 시 JWT가 아니라 문자열 `"ok"`를 반환합니다. 따라서 `const token`은 실제로 재발급 성공 여부를 판단하는 값이고 인증 토큰은 쿠키에 있습니다.

재발급이 HTTP 실패로 끝나 null을 반환하면 원래 오류 응답을 호출부로 돌려줍니다. 로그인 안내·이동은 화면 코드의 책임입니다. 네트워크 예외는 여기서 null로 변환하지 않으므로 호출부의 예외 처리도 필요합니다.

재시도는 일반 `apiFetch()`로 한 번 수행하므로 자동 재발급을 무한 반복하지 않습니다. refresh 주소 자체도 자동 재발급에서 제외합니다.

403은 토큰 만료 외에 CSRF 실패·소유권 부족으로도 발생합니다. 새 토큰으로 권한 부족이 해결되지는 않습니다. 현재 동작은 401·403에 한 번 시도하는 것이며, 이 함수를 사용하는 요청에만 적용됩니다.

## 1-6. 동시에 발생한 토큰 재발급 요청을 하나로 모으기

화면 하나가 여러 종류의 정보를 표시하면 API 요청도 여러 개일 수 있습니다.

```text
사용자가 프로필 페이지를 한 번 열기
→ 기본 프로필 조회 요청
→ 필모그래피 조회 요청
→ 갤러리 조회 요청
```

이는 화면과 요청이 반드시 일대일이 아니라는 예시입니다. 현재 위 조회들은 공개 API이므로 그 자체로 재발급 사례는 아닙니다. 보호된 API 여러 개가 동시에 인증 실패를 받을 때 아래 문제가 생깁니다.

```text
A 요청: 재발급 성공, 기존 Refresh Token 폐기
B 요청: 같은 Refresh Token으로 재발급, 충돌 또는 재사용 거부
C 요청: 같은 Refresh Token으로 재발급, 충돌 또는 재사용 거부
```

프론트엔드는 진행 중인 재발급을 공유합니다.

```javascript
let refreshInFlight = null;
```

in flight는 진행 중이라는 뜻입니다. 이 변수에는 토큰이 아니라 Promise를 저장합니다. Promise는 나중에 완료될 작업의 결과를 기다릴 수 있는 객체입니다.

실제 함수에서 중복된 상태 검사 한 줄을 생략한 핵심 코드입니다.

```javascript
async function refreshAccessToken() {
    if (refreshInFlight) return refreshInFlight;

    refreshInFlight = (async () => {
        const res = await apiFetch(AUTH.refresh, { method: "POST" });
        if (res.status !== 200) return null;
        await safeReadBody(res);
        setAccessToken(null);
        return "ok";
    })();

    try {
        return await refreshInFlight;
    } finally {
        refreshInFlight = null;
    }
}
```

첫 요청은 새 작업을 만들고 Promise를 저장합니다. `(async () => { ... })()`의 마지막 괄호는 만든 함수를 즉시 실행한다는 의미입니다.

뒤이어 호출한 요청은 진행 중인 Promise를 반환받아 같은 작업의 결과를 기다립니다.

```text
A → 재발급 시작 ─────────────┐
B → 같은 작업 결과 기다림 ────┼→ 재발급 완료·쿠키 교체
C → 같은 작업 결과 기다림 ────┘
→ A·B·C는 각자의 원래 요청을 재시도
```

`finally`에서는 성공·실패와 관계없이 진행 중 표시를 지웁니다. 나중에 다시 만료되면 새로운 재발급을 시작할 수 있어야 하기 때문입니다.

공유 범위는 현재 페이지의 JavaScript 실행 문맥입니다. 서로 다른 탭까지 조정하지 않으며, 재발급 완료 후 늦게 도착한 실패 응답은 새 재발급을 시작할 수 있습니다. 서버의 낙관적 락과 재사용 검사는 여전히 필요합니다.

## 1-7. 만료된 Refresh Token의 사용 차단과 DB 정리

만료되면 즉시 재발급에 사용할 수 없지만 DB 행이 그 순간 자동으로 삭제되지는 않습니다.

```text
사용 차단 → 요청마다 expiresAt·revokedAt 검사
기록 정리 → 일정 시간이 지난 기록을 예약 작업으로 삭제
```

Rotation으로 폐기된 토큰을 잠시 보관해야 나중에 같은 원문이 들어왔을 때 소유자와 폐기 상태를 확인할 수 있습니다. 바로 삭제하면 알 수 없는 토큰으로 거부할 수는 있지만 사용자 전체 토큰 삭제 등의 재사용 대응 정보는 잃습니다.

`RefreshTokenCleanupService`가 오래된 기록을 삭제합니다.

```java
@Scheduled(cron = "${auth.refresh-token-cleanup-cron:0 0 4 * * *}")
@Transactional
public int deleteOldTokens() {
    Duration retention = authProperties.refreshTokenRetention();
    if (retention == null) {
        retention = DEFAULT_RETENTION;
    }
    if (retention.isNegative() || retention.isZero()) {
        throw new IllegalStateException("refresh token retention must be positive");
    }
    Instant cutoff = clock.instant().minus(retention);
    int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
    // 삭제 개수 로그 생략
    return deleted;
}
```

기본 보관 기간은 30일, 실행 일정은 매일 오전 4시입니다. `@Scheduled`는 정해진 시간에 메서드를 실행합니다. zone이 명시되지 않아 스케줄러의 기본 시간대가 적용되며 서버가 실행 중이어야 합니다.

```yaml
refresh-token-retention: 30d
refresh-token-cleanup-cron: "0 0 4 * * *"
```

```text
현재 시각 - 30일 = cutoff(삭제 기준 시각)
```

```sql
delete from RefreshToken rt
where rt.expiresAt <= :cutoff
   or (rt.revokedAt is not null and rt.revokedAt <= :cutoff)
```

만료된 지 30일 이상이거나 폐기된 지 30일 이상이면 삭제합니다. 둘 중 하나만 충족해도 됩니다. 발급일 기준 30일이 아닙니다.

| 상태 | 삭제 대상 |
|---|---|
| 어제 만료, 아직 폐기되지 않음 | 아직 보관 |
| 31일 전 만료 | 삭제 |
| 31일 전 폐기 | 삭제 |
| 유효하고 미폐기 | 유지 |

30일은 사용할 수 있는 추가 기간이 아니라 기록 보관 기준입니다. 재사용 탐지 시 전체 삭제처럼 이 정기 보관 정책보다 먼저 기록을 제거하는 경로도 있습니다.

## 1-8. 토큰 관리 테스트 읽기

테스트는 다음 순서로 읽습니다.

```text
준비: 어떤 상황을 만들었는가?
실행: 어떤 기능을 호출했는가?
확인: 어떤 결과를 기대하는가?
```

### 만료 경계 시각 검사

RefreshTokenTest는 정확히 만료 시각에 도달한 경우도 만료로 보는지 검사합니다.

```java
@Test
void expiration_includesExactExpirationInstant() {
    RefreshToken token = RefreshToken.issue(
        1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT);

    assertThat(token.isExpiredAt(EXPIRES_AT.minusNanos(1))).isFalse();
    assertThat(token.isExpiredAt(EXPIRES_AT)).isTrue();
    assertThat(token.isExpiredAt(EXPIRES_AT.plusNanos(1))).isTrue();
}
```

`@Test`는 테스트 메서드 표시이고 `assertThat(...).isTrue()`는 결과가 true인지 검사합니다. 만료 직전·정확한 시각·직후를 확인해 `>`와 `>=` 같은 경계 실수를 잡습니다. 자바 객체만 사용하는 테스트라 DB가 필요하지 않습니다.

### 사용 처리와 반복 사용 거부

```java
token.consume(usedAt);
assertThat(token.getLastUsedAt()).isEqualTo(usedAt);
assertThat(token.getRevokedAt()).isEqualTo(usedAt);

assertThatThrownBy(() -> token.consume(usedAt.plusSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("refresh token already revoked");
```

처음 사용한 시각이 두 필드에 남고 두 번째 사용은 거부되는지 봅니다. `assertThatThrownBy`는 예상한 예외가 발생해야 성공입니다.

### Service의 판단과 호출 확인

RefreshTokenServiceTest는 `@Mock`으로 만든 Repository 등의 대역에 원하는 응답을 지정합니다.

```java
assertThatThrownBy(() -> refreshTokenService.rotate(OLD_RAW_TOKEN, TTL))
        .isInstanceOf(RefreshTokenReuseDetectedException.class);

verify(securityTransactionService).deleteAllSessionsForReuse(1L);
verify(jwtProvider, never()).createRefreshToken();
```

이미 폐기된 토큰을 조회하도록 준비한 뒤 재사용 예외, 전체 삭제 요청, 새 토큰 미발급을 확인합니다.

- `verify(...)`: 해당 메서드 호출 확인
- `never()`: 호출하지 않았는지 확인
- Mock: 실제 DB 대신 미리 정한 결과를 반환하는 테스트 대역

삭제 메서드를 호출했다는 검증과 DB에서 실제 삭제됐다는 검증은 다릅니다. 이 테스트만으로 트랜잭션 커밋까지 증명할 수는 없습니다.

시간은 `Clock.fixed(...)`로 고정해 실행 시각에 따라 테스트 결과가 바뀌지 않게 합니다. 정리 Service 테스트도 고정 시각에서 30일을 뺀 cutoff와 삭제 개수를 확인하며, 보관 기간이 0이면 거부하는지 검사합니다.

### 재발급 실패 후에도 기록이 남는가?

RefreshTokenExpirationPersistenceTest는 Spring과 테스트 DB를 사용합니다.

```text
만료된 토큰을 DB에 저장
→ rotate() 실행
→ InvalidRefreshTokenException 확인
→ DB에서 토큰 다시 조회
→ revokedAt과 lastUsedAt이 남아 있는지 확인
```

```java
RefreshToken reloaded = refreshTokenRepository.findById(tokenId).orElseThrow();
assertThat(reloaded.getRevokedAt()).isNotNull();
assertThat(reloaded.getLastUsedAt()).isEqualTo(reloaded.getRevokedAt());
```

메모리에서 값이 바뀐 것만 보지 않고 다시 조회해 별도 트랜잭션 기록의 지속성을 확인합니다.

### 동시 재발급 테스트

RefreshTokenConcurrencyTest는 회원가입·로그인 후 같은 토큰으로 두 요청을 준비하고 CountDownLatch로 출발 시점을 맞춥니다. MockMvc는 실제 네트워크 서버 대신 Spring의 HTTP 처리 경로를 테스트합니다.

```java
assertThat(statuses).containsExactlyInAnyOrder(200, 401);
assertThat(statuses).doesNotContain(500);
```

또한 미폐기 토큰 수가 1인지 확인합니다. 하지만 동시에 출발하는 것과 같은 version을 동시에 조회하는 것은 다릅니다. 늦은 요청이 폐기 후 조회하면 전체 삭제 경로로 가므로, 테스트의 실행 순서에 따라 미폐기 1개 가정이 흔들릴 수 있습니다.

같은 테스트 파일에는 순차 재사용 후 해당 사용자의 토큰이 모두 삭제되는지 확인하는 테스트도 있습니다.

현재 기본 테스트 설정은 H2입니다. 이 테스트들이 MySQL 운영 환경의 모든 잠금·제약조건 동작까지 입증하지는 않습니다. MySQL 검증은 별도 integrationTest와 구분해야 합니다. 이번 문서 작성에서는 테스트를 새로 실행하지 않았습니다.

## 1-9. 로그인부터 로그아웃까지 전체 흐름과 현재 구현의 한계

### 사용자 한 명의 전체 흐름

```text
회원가입
→ DTO·값 객체·비밀번호 정책 검사
→ 중복 확인·BCrypt 해싱
→ User·Person 함께 저장

로그인
→ 이메일 조회·BCrypt 비교
→ Access JWT + Refresh 원문 + CSRF 쿠키 발급
→ Refresh 해시와 상태는 DB 저장

보호된 API 이용
→ 쿠키·CSRF 헤더 전송
→ CSRF·인증·접근 권한 검사
→ 필요한 기능에서는 소유권도 검사
→ 업무 처리

Access Token 만료 후 요청
→ 자동 재발급 함수를 사용하는 요청에서 실패 감지
→ Refresh Token Rotation
→ 새 쿠키로 원래 요청 재시도

로그아웃
→ 현재 Refresh Token 폐기
→ Access·Refresh·CSRF 쿠키 삭제

정기 정리
→ 만료·폐기 후 보관 기준이 지난 기록 삭제
```

### 설명할 때 구분할 점

1. **로그아웃과 Access Token 즉시 무효화는 다릅니다.** 현재는 복사된 JWT가 만료 전까지 유효할 수 있습니다. Refresh Token 전체 삭제도 기존 JWT를 바로 차단하지는 않습니다.
2. **재사용 탐지는 탈취를 확정한 것이 아닙니다.** 중복 요청이나 커밋 후 응답 유실도 원인일 수 있습니다. 현재 정책은 전체 Refresh Token을 삭제해 정상 사용자도 다시 로그인해야 할 수 있습니다.
3. **동시 요청은 실행 순서에 따라 다른 경로를 탑니다.** 같은 version을 읽으면 낙관적 락 충돌, 폐기 후 읽으면 재사용 탐지입니다.
4. **프론트엔드의 요청 공유는 현재 페이지 범위입니다.** 다른 탭이나 완료 후 늦게 도착한 실패 응답까지 모두 합치지는 않습니다.
5. **403 재시도가 모든 문제를 해결하지 않습니다.** 권한 부족이면 새 토큰으로도 실패합니다. 일반 fetch에는 자동 재발급이 적용되지 않습니다.
6. **CSRF 제외와 출처 비교는 보완 대상입니다.** 인증 경로의 startsWith 제외, 스킴·포트가 엄격하지 않은 비교, 단순 쿠키·헤더 일치 방식을 완전한 보호라고 설명하지 않습니다.
7. **Secure는 권장값과 현재 설정을 구분합니다.** 소스의 prod 기본값은 테스트 목적으로 false입니다. HTTPS 배포 시 실제 적용값을 확인해야 합니다.
8. **GET 로그아웃을 허용하고 있습니다.** 상태 변경을 POST로 제한하고 CSRF 정책을 함께 검토할 여지가 있습니다.
9. **테스트 코드의 존재와 실행 결과는 다릅니다.** Mock 호출 검증·H2 트랜잭션 테스트·MySQL 통합 검증의 범위를 구분합니다.

회원가입·로그인·토큰 관리 학습은 여기까지입니다. 다음 단원은 **2-1. 계정 User와 프로필 Person을 분리한 이유**입니다.

## 관련 코드와 문서

- [AuthController](../../src/main/java/com/onfilm/domain/auth/controller/AuthController.java)
- [AuthService](../../src/main/java/com/onfilm/domain/auth/service/AuthService.java)
- [AuthTransactionService](../../src/main/java/com/onfilm/domain/auth/service/AuthTransactionService.java)
- [SignupRequest](../../src/main/java/com/onfilm/domain/auth/dto/SignupRequest.java)
- [User](../../src/main/java/com/onfilm/domain/user/entity/User.java)
- [UserEmail](../../src/main/java/com/onfilm/domain/user/entity/UserEmail.java)
- [Username](../../src/main/java/com/onfilm/domain/user/entity/Username.java)
- [RawPasswordPolicy](../../src/main/java/com/onfilm/domain/user/entity/RawPasswordPolicy.java)
- [JwtProvider](../../src/main/java/com/onfilm/domain/auth/security/JwtProvider.java)
- [JwtAuthFilter](../../src/main/java/com/onfilm/domain/auth/security/JwtAuthFilter.java)
- [SecurityConfig](../../src/main/java/com/onfilm/domain/auth/config/SecurityConfig.java)
- [AuthCookieFactory](../../src/main/java/com/onfilm/domain/auth/infrastructure/AuthCookieFactory.java)
- [CsrfProtectionFilter](../../src/main/java/com/onfilm/domain/auth/security/CsrfProtectionFilter.java)
- [CurrentPersonProvider](../../src/main/java/com/onfilm/domain/movie/service/CurrentPersonProvider.java)
- [RefreshToken](../../src/main/java/com/onfilm/domain/token/entity/RefreshToken.java)
- [RefreshTokenService](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenService.java)
- [RefreshTokenSecurityTransactionService](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenSecurityTransactionService.java)
- [RefreshTokenCleanupService](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenCleanupService.java)
- [RefreshTokenRepository](../../src/main/java/com/onfilm/domain/token/repository/RefreshTokenRepository.java)
- [프론트엔드 auth.js](../../src/main/resources/static/js/auth.js)
- [RefreshTokenTest](../../src/test/java/com/onfilm/domain/token/entity/RefreshTokenTest.java)
- [RefreshTokenServiceTest](../../src/test/java/com/onfilm/domain/token/service/RefreshTokenServiceTest.java)
- [RefreshTokenCleanupServiceTest](../../src/test/java/com/onfilm/domain/token/service/RefreshTokenCleanupServiceTest.java)
- [RefreshTokenExpirationPersistenceTest](../../src/test/java/com/onfilm/domain/token/RefreshTokenExpirationPersistenceTest.java)
- [RefreshTokenConcurrencyTest](../../src/test/java/com/onfilm/domain/token/RefreshTokenConcurrencyTest.java)
- [운영 설정](../../src/main/resources/application-prod.yml)
- [기본 테스트 설정](../../src/test/resources/application.yml)
- [전체 공부 순서](./00-study-roadmap.md)
