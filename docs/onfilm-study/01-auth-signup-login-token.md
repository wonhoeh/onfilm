# 1. 회원가입·로그인·토큰 관리

작성일: 2026-09-17

이 문서는 **핵심 설명을 먼저 읽고, 막히는 부분만 원문을 펼쳐 보는 학습 노트**입니다. 각 항목은 핵심 설명 → 짧은 코드·설계 이유 → 접어 둔 상세 원문 순서로 구성했습니다. 짧은 코드는 흐름을 이해하기 위한 발췌·예시이며, 전체 맥락은 상세 원문에서 확인할 수 있습니다.

상세 원문에는 반복 설명, 코드 예시, 직접 질문했던 보충 설명을 모두 보존했습니다. 원문의 중복된 번호도 유지했으므로 아래 목차로 이동하면 됩니다. 처음부터 끝까지 상세 원문을 모두 읽을 필요는 없습니다.

접기·펼치기는 이를 지원하는 Markdown 미리보기에서 동작합니다. 원문 표현의 정정 사항은 문서 끝의 검토 메모에 따로 두었으며, 기존 [요약·검토본](./01-auth-signup-login-token-summary.md)도 남겨 두었습니다.

## 목차

- [1.1 회원가입 요청의 전체 흐름](#lesson-01)
- [1.2 회원가입 입력값 검증](#lesson-02)
- [1.3 UserEmail과 Username 값 객체가 있는 이유](#lesson-03)
- [1.4 비밀번호를 안전하게 저장하는 방법](#lesson-04)
- [1.5 이메일·사용자 이름 중복 검사](#lesson-05)
- [1.6 User와 기본 Person 생성](#lesson-06)
- [1.2 로그인 전체 흐름](#lesson-07)
- [1.2-1 Access Token과 JWT 인증 흐름](#lesson-08)
- [1.2-2 Access Token과 Refresh Token을 나눈 이유](#lesson-09)
- [1.3 Refresh Token Rotation](#lesson-10)
- [1.3-1 폐기된 Refresh Token 재사용 탐지](#lesson-11)
- [1.3-2 Refresh Token 동시 요청과 낙관적 락](#lesson-12)
- [1.3-3 만료된 Refresh Token 처리](#lesson-13)
- [1.3-4 로그아웃 처리](#lesson-14)
- [1.4 쿠키 보안 옵션](#lesson-15)
- [1-3. 쿠키 인증과 CSRF 방어](#lesson-16)
- [1-4. 인증과 인가, 프로필 소유권 검사](#lesson-17)
- [1-5. 프론트엔드의 자동 재발급과 요청 재시도](#lesson-18)
- [1-6. 동시에 발생한 토큰 재발급 요청을 하나로 모으기](#lesson-19)
- [1-7. 만료된 Refresh Token의 사용 차단과 DB 정리](#lesson-20)
- [1-8. 토큰 관리 테스트 읽기](#lesson-21)
- [1-9. 로그인부터 로그아웃까지 전체 흐름과 현재 구현의 한계](#lesson-22)

---

<!-- 학습 본문 시작 -->

<a id="lesson-01"></a>

## 1.1 회원가입 요청의 전체 흐름

회원가입은 **입력 검사 → 중복 확인 → 비밀번호 해싱 → 계정과 기본 프로필 저장** 순서로 진행합니다. 성공하면 본문 없이 `201 Created`를 반환하며, 로그인은 별도로 요청합니다.

```java
transactionService.validateSignupAvailability(email, username);
String encodedPassword = passwordEncoder.encode(rawPassword);
transactionService.register(email, encodedPassword, username);
```

`register()` 안에서 중복을 다시 확인하고 User와 Person을 같은 트랜잭션에 저장합니다. 둘 중 하나의 저장이 실패하면 함께 롤백됩니다.

<details>
<summary>상세히 보기: 요청이 들어온 뒤 User와 Person이 저장되기까지</summary>

<!-- 학습 원문 시작: lesson-01 -->

사용자는 이메일, 비밀번호, 사용자 이름을 입력해 회원가입합니다.

```http
POST /auth/signup
Content-Type: application/json
```

```json
{
  "email": "Test3@OnFilm.local",
  "password": "testPassword123!",
  "username": "test3"
}
```

회원가입 전체 흐름은 다음과 같습니다.

```text
회원가입 요청
→ SignupRequest 형식 검사
→ 이메일과 사용자 이름 정리
→ 비밀번호 규칙 검사
→ 이메일·사용자 이름 중복 확인
→ 비밀번호 암호화
→ User 생성
→ 기본 Person 생성
→ User와 Person 저장
→ HTTP 201 Created
```

---

### 1. Controller가 요청을 받음

```java
@PostMapping("/signup")
public ResponseEntity<Void> signup(
        @Valid @RequestBody SignupRequest request
) {
    authService.signup(request);

    return ResponseEntity
            .status(HttpStatus.CREATED)
            .build();
}
```

각 코드의 의미는 다음과 같습니다.

```java
@PostMapping("/signup")
```

클래스의 기본 주소가 /auth이므로 전체 주소는 다음과 같습니다.

```http
POST /auth/signup
```

```java
@RequestBody SignupRequest request
```

사용자가 보낸 JSON을 SignupRequest 객체로 변환합니다.

```java
@Valid
```

Service를 호출하기 전에 요청값을 검사합니다.

회원가입이 성공하면:

```text
HTTP 201 Created
응답 본문 없음
```

201은 새로운 데이터가 정상적으로 생성됐다는 의미입니다.

---

### 2. SignupRequest가 입력값을 검사

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
    @Size(
        min = Username.MIN_LENGTH,
        max = Username.MAX_LENGTH
    )
    @Pattern(regexp = Username.PATTERN_VALUE)
    String username
) {
}
```

#### 이메일

```java
@NotBlank
@Email
@Size(max = 254)
```

- 빈 문자열 금지
- 이메일 형식 검사
- 최대 254자

#### 비밀번호

```java
@NotBlank
@ValidRawPassword
```

- 빈 문자열 금지
- OnFilm 비밀번호 정책 검사

#### 사용자 이름

```java
@NotBlank
@Size(min = 3, max = 20)
@Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$")
```

- 3자 이상 20자 이하
- 영문자, 숫자, _, -만 허용

검증에 실패하면 Service는 실행되지 않고 422 응답을 반환합니다.

---

### 3. AuthService가 회원가입 절차를 진행

```java
public void signup(SignupRequest request) {
    UserEmail email =
            UserEmail.from(request.email());

    Username username =
            Username.from(request.username());

    String rawPassword =
            RawPasswordPolicy.validate(request.password());

    transactionService.validateSignupAvailability(
        email,
        username
    );

    String encodedPassword =
            passwordEncoder.encode(rawPassword);

    transactionService.register(
        email,
        encodedPassword,
        username
    );
}
```

이 메서드는 회원가입 작업의 실행 순서를 정합니다.

---

### 4. 이메일을 정리

```java
UserEmail email =
        UserEmail.from(request.email());
```

입력값이 다음과 같아도:

```text
"  Test3@OnFilm.local  "
```

앞뒤 공백을 제거하고 소문자로 바꿉니다.

```text
test3@onfilm.local
```

이 과정을 정규화라고 합니다.

정규화하지 않으면 다음 이메일을 서로 다른 값으로 판단할 수 있습니다.

```text
Test3@OnFilm.local
test3@onfilm.local
```

---

### 5. 사용자 이름을 정리

```java
Username username =
        Username.from(request.username());
```

Username은 두 값을 관리합니다.

```text
value      : 화면에 사용할 원래 값
normalized : 중복 비교에 사용할 소문자 값
```

예를 들어:

```text
입력        : FilmActor
value      : FilmActor
normalized : filmactor
```

따라서 다음 두 이름을 중복으로 판단할 수 있습니다.

```java
FilmActor
filmactor
```

---

### 6. 비밀번호 정책 검사

```java
String rawPassword =
        RawPasswordPolicy.validate(request.password());
```

현재 정책은 다음과 같습니다.

```text
최소 길이         : 8자
BCrypt 입력 크기  : UTF-8 기준 최대 72바이트
```

한글과 영문은 바이트 수가 다를 수 있으므로 단순 글자 수뿐 아니라 UTF-8 바이트 수도 검사합니다.

---

### 7. 중복 확인

```java
transactionService.validateSignupAvailability(
    email,
    username
);
```

Repository에서 이메일과 사용자 이름이 이미 존재하는지 확인합니다.

```java
userRepository.existsByEmail(email.value());

userRepository.existsByUsernameNormalized(
    username.normalized()
);
```

중복 이메일이면:

```text
DuplicateEmailException
→ HTTP 409 Conflict
```

중복 사용자 이름이면:

```text
DuplicateUsernameException
→ HTTP 409 Conflict
```

---

### 8. 비밀번호 암호화

```java
String encodedPassword =
        passwordEncoder.encode(rawPassword);
```

사용자가 입력한 비밀번호 원문을 그대로 DB에 저장하지 않습니다.

입력 비밀번호
testPassword123!

암호화 후에는 다음과 비슷한 값이 됩니다.

$2a$10$3sG7...생략...

DB에는 암호화된 결과만 저장합니다.

```text
rawPassword     → DB 저장 금지
encodedPassword → DB 저장
```

---

### 9. User와 기본 Person 생성

```java
User user = User.create(
    email,
    encodedPassword,
    username
);

user.createPerson(username.value());
```

먼저 로그인 계정을 생성합니다.

```text
User
├─ email
├─ encodedPassword
├─ username
└─ usernameNormalized
```

그다음 기본 프로필을 생성합니다.

```text
Person
├─ name = username
├─ publicId = UUID
└─ 나머지 프로필 정보 = 초기값
```

회원가입 직후에도 계정에 프로필이 항상 연결되도록 두 객체를 함께 만듭니다.

```text
User 1 ─── 1 Person
```

---

### 10. DB 저장

```java
userRepository.saveAndFlush(user);
```

User에 다음 설정이 있습니다.

```java
@OneToOne(cascade = CascadeType.ALL)
private Person person;
```

따라서 User를 저장하면 연결된 Person도 함께 저장됩니다.

```text
person 테이블
→ 기본 프로필 INSERT

users 테이블
→ 계정 정보와 person_id INSERT
```

saveAndFlush()는 INSERT SQL을 이 메서드 안에서 실제 DB에 보내도록 합니다.

DB Unique Constraint 위반이 있으면 여기서 예외가 발생하고, OnFilm의 중복 이메일 또는 사용자 이름 오류로 변환됩니다.

---

### 11. 성공 응답

모든 저장이 성공하면 Controller가 응답합니다.

```http
HTTP/1.1 201 Created
```

응답 본문은 없습니다.

회원가입이 로그인까지 자동으로 수행하는 것은 아닙니다. 사용자는 가입 후 별도의 로그인 요청을 보냅니다.

---

### 전체 코드 흐름

```http
POST /auth/signup
        ↓
AuthController.signup()
        ↓
SignupRequest 검증
        ↓
AuthService.signup()
        ├─ UserEmail.from()
        ├─ Username.from()
        ├─ RawPasswordPolicy.validate()
        └─ PasswordEncoder.encode()
        ↓
AuthTransactionService.register()
        ├─ 중복 재확인
        ├─ User.create()
        ├─ user.createPerson()
        └─ userRepository.saveAndFlush()
        ↓
MySQL
        ├─ person INSERT
        └─ users INSERT
        ↓
HTTP 201 Created
```

### 한 줄 요약

> OnFilm 회원가입은 요청값을 검사하고 이메일과 사용자 이름을 정리한 뒤 비밀번호를 암호화하며, 로그인 계정인 User와 기본 프로필인 Person을 하나의 트랜잭션으로 함께 저장합니다.

<!-- 학습 원문 끝: lesson-01 -->

</details>

---

<a id="lesson-02"></a>

## 1.2 회원가입 입력값 검증

DTO는 잘못된 HTTP 입력을 입구에서 막고, 값 객체와 정책 클래스는 다른 내부 코드에서도 같은 규칙을 지킵니다. **두 검사는 적용되는 위치가 다릅니다.**

```java
@NotBlank
@Email
@Size(max = UserEmail.MAX_LENGTH)
String email;
```

DTO 검증 실패는 `422`와 필드 오류로 응답합니다. 값 객체는 검사뿐 아니라 정규화도 수행합니다. 단, HTTP 입력의 앞뒤 공백은 정규화 전에 DTO에서 거부될 수 있습니다.

<details>
<summary>상세히 보기: @Valid·@NotBlank·@Email과 사용자 정의 비밀번호 검증</summary>

<!-- 학습 원문 시작: lesson-02 -->

OnFilm의 회원가입에서는 입력값을 두 단계로 검사합니다.

```text
1단계: SignupRequest
→ HTTP 요청 형식 검사

2단계: UserEmail, Username, RawPasswordPolicy
→ 서비스 내부 규칙 검사
```

겉으로 보면 같은 값을 두 번 검사하는 것처럼 보이지만 목적이 다릅니다.

---

### 1. SignupRequest의 검증

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
    @Size(
        min = Username.MIN_LENGTH,
        max = Username.MAX_LENGTH
    )
    @Pattern(regexp = Username.PATTERN_VALUE)
    String username
) {
}
```

Controller에서 @Valid가 붙어 있습니다.

```java
public ResponseEntity<Void> signup(
        @Valid @RequestBody SignupRequest request
)
```

따라서 Controller가 요청을 받은 직후 DTO 검증이 실행됩니다.

```text
JSON 수신
→ SignupRequest 생성
→ @Valid 검사
→ 성공하면 AuthService 호출
→ 실패하면 HTTP 422 반환
```

---

### 2. @NotBlank

```java
@NotBlank
String email;
```

다음 값을 모두 거부합니다.

```text
null
""
"   "
```

단순히 비어 있는지만 보는 것이 아니라, 공백으로만 이루어진 문자열도 거부합니다.

---

### 3. @Email

```java
@Email
String email;
```

입력값이 이메일 형식인지 검사합니다.

```text
user@example.com  → 허용
not-an-email      → 거부
user@             → 거부
```

---

### 4. @Size

```java
@Size(max = UserEmail.MAX_LENGTH)
String email;
```

문자열 길이를 검사합니다.

```text
UserEmail.MAX_LENGTH // 254
```

사용자 이름에는 최소 길이와 최대 길이가 모두 적용됩니다.

```java
@Size(min = 3, max = 20)
String username;
```

```text
ab                    → 너무 짧아서 거부
test_user             → 허용
123456789012345678901 → 너무 길어서 거부
```

---

### 5. @Pattern

```java
@Pattern(
    regexp = "^[a-zA-Z0-9_-]{3,20}$"
)
String username;
```

사용할 수 있는 문자를 제한합니다.

```text
test_user-1 → 허용
test user   → 공백이 있어 거부
테스트      → 한글이 있어 거부
test!       → !가 있어 거부
```

정규식은 다음 의미입니다.

```text
^              문자열 시작
[a-zA-Z0-9_-]  영문자, 숫자, _, -
{3,20}         3자 이상 20자 이하
$              문자열 끝
```

---

### 사용자 정의 검증 @ValidRawPassword

Jakarta Validation에는 OnFilm의 비밀번호 규칙을 정확히 검사하는 기본 애너테이션이 없습니다.

그래서 사용자 정의 애너테이션을 만들었습니다.

```java
@ValidRawPassword
String password;
```

### 애너테이션 정의

```java
@Constraint(validatedBy = RawPasswordValidator.class)
public @interface ValidRawPassword {

    String message() default
        "비밀번호는 8자 이상이며 UTF-8 기준 72바이트 이하여야 합니다.";
}
```

이 애너테이션이 붙은 값은 RawPasswordValidator가 검사합니다.

---

### 실제 검사 클래스

```java
public class RawPasswordValidator
        implements ConstraintValidator<
            ValidRawPassword,
            String
        > {

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
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
}
```

검사 결과는 다음과 같습니다.

```text
규칙 통과                  → true
IllegalArgumentException → false
```

false가 반환되면 요청값 검증 실패로 처리됩니다.

---

### null인데 왜 true를 반환할까?

```java
if (value == null) {
    return true;
}
```

null 검사는 이미 @NotBlank가 담당합니다.

```java
@NotBlank
@ValidRawPassword
String password;
```

RawPasswordValidator까지 null을 거부하면 하나의 값에 비슷한 오류 메시지가 두 개 생길 수 있습니다.

역할을 다음처럼 나눈 것입니다.

```java
@NotBlank
→ null, 빈 문자열, 공백 검사

@ValidRawPassword
→ 비밀번호 길이와 바이트 수 검사
```

---

### DTO 다음에 다시 검사하는 이유

DTO 검증을 통과한 뒤 AuthService에서 다시 다음 코드를 실행합니다.

```java
UserEmail email =
        UserEmail.from(request.email());

Username username =
        Username.from(request.username());

String rawPassword =
        RawPasswordPolicy.validate(request.password());
```

왜 다시 검사할까요?

### DTO 검증은 HTTP 요청의 입구를 보호

SignupRequest는 외부에서 잘못된 HTTP 요청이 들어오는 것을 빠르게 차단합니다.

```text
사용자가 잘못된 이메일 전송
→ Controller 입구에서 차단
→ 읽기 쉬운 필드 오류 응답 제공
```

예를 들면 다음과 같습니다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    {
      "field": "email",
      "message": "올바른 형식의 이메일 주소여야 합니다."
    }
  ]
}
```

### 값 객체는 애플리케이션 내부를 보호

UserEmail과 Username은 Controller 밖에서도 생성될 수 있습니다.

```text
테스트 코드
개발 초기 데이터
다른 Service
향후 Batch 프로그램
```

이런 코드는 SignupRequest와 @Valid를 거치지 않을 수 있습니다.

```java
UserEmail.from("잘못된 이메일");
```

그래도 UserEmail 자체가 잘못된 값을 거부하기 때문에 유효하지 않은 이메일 객체가 만들어지지 않습니다.

---

### UserEmail은 정규화까지 담당

```java
String normalized =
        rawEmail.trim().toLowerCase(Locale.ROOT);
```

DTO 검증은 형식만 확인하지만 UserEmail은 실제 저장할 형태로 값을 정리합니다.

```text
"  User@Example.COM  "
→ "user@example.com"
```

---

### Username은 원본과 비교용 값을 만듦

```java
String value = rawUsername.trim();

return new Username(
    value,
    value.toLowerCase(Locale.ROOT)
);
```

```text
value      = "TestUser"
normalized = "testuser"
```

- value: 화면에 표시하고 저장할 사용자 이름
- normalized: 대소문자를 무시하고 중복을 검사할 값

---

### 검증 책임 정리

```text
SignupRequest
→ HTTP 요청 형식이 올바른지 검사
→ 어떤 필드가 잘못됐는지 응답

UserEmail
→ 유효한 이메일만 객체로 생성
→ 공백 제거와 소문자 변환

Username
→ 유효한 사용자 이름만 객체로 생성
→ 표시값과 중복 비교값 생성

RawPasswordPolicy
→ 비밀번호 규칙을 한곳에서 관리

DB Constraint
→ 동시에 같은 값이 저장되는 것까지 최종 차단
```

### 한 줄 요약

> DTO 검증은 잘못된 HTTP 요청을 입구에서 막고, 값 객체와 정책 클래스는 DTO를 거치지 않는 내부 코드에서도 잘못된 값이 생성되지 않도록 보호합니다.

<!-- 학습 원문 끝: lesson-02 -->

</details>

---

<a id="lesson-03"></a>

## 1.3 UserEmail과 Username 값 객체가 있는 이유

이메일과 사용자 이름을 전용 타입으로 만들면 서로 바꿔 전달하는 실수를 줄이고, **검증·정규화를 거친 값만 내부에서 사용**하게 할 수 있습니다.

```java
UserEmail email = UserEmail.from(request.email());
Username username = Username.from(request.username());
// username.value()      → 표시용 이름
// username.normalized() → 소문자로 만든 중복 비교용 이름
```

private 생성자로 생성 경로를 제한하고 final 필드로 값을 유지합니다. 현재 두 클래스는 별도 Entity가 아니며, 값 기반 equals()도 구현하지 않았습니다. User에는 내부 문자열을 꺼내 저장합니다.

<details>
<summary>상세히 보기: String 대신 값 객체를 쓰는 이유, final과 private 생성자</summary>

<!-- 학습 원문 시작: lesson-03 -->

회원가입 요청에서 이메일과 사용자 이름은 처음에는 String입니다.

```java
request.email();
request.username();
```

하지만 AuthService에서는 그대로 사용하지 않고 전용 객체로 바꿉니다.

```java
UserEmail email =
        UserEmail.from(request.email());

Username username =
        Username.from(request.username());
```

---

### 그냥 String을 사용하면 안 될까?

기술적으로는 가능합니다.

```java
public void register(
        String email,
        String username,
        String password
) {
}
```

하지만 모두 String이라 다음과 같은 실수가 가능합니다.

```java
register(username, email, password);
```

컴파일러는 첫 번째와 두 번째 값이 모두 String이므로 잘못된 순서를 알아차리지 못합니다.

전용 타입으로 바꾸면 의미가 분명해집니다.

```java
public void register(
        UserEmail email,
        Username username,
        String encodedPassword
) {
}
```

```text
UserEmail → 이메일
Username  → 사용자 이름
```

---

### UserEmail 구조

```java
public final class UserEmail {

    private final String value;

    private UserEmail(String value) {
        this.value = value;
    }

    public static UserEmail from(String rawEmail) {
        // 검사와 정규화
        return new UserEmail(normalized);
    }

    public String value() {
        return value;
    }
}
```

### final class

```java
public final class UserEmail
```

다른 클래스가 UserEmail을 상속해 동작을 바꾸지 못하게 합니다.

이메일 규칙을 하나의 클래스 안에서 일정하게 유지하기 위한 선택입니다.

- 추가설명
상속해서 동작을 바꾼다는 것은 다른 클래스가 UserEmail을 부모로 삼고, 기존 메서드를 다시 정의하는 것을 말합니다.

만약 final이 없고 생성자 접근이 가능하다면 다음과 같은 클래스를 만들 수 있습니다.

```java
class StrangeEmail extends UserEmail {

    @Override
    public String value() {
        return "invalid-email";
    }
}
```

원래 UserEmail.value()는 검증된 이메일을 반환해야 하는데, 자식 클래스가 메서드를 재정의하면 다른 값을 반환할 수 있습니다.
이런 메서드 재정의를 오버라이딩이라고 합니다.

하지만 현재 클래스에는 final이 붙어 있습니다.

```java
public final class UserEmail {
}
```

따라서 다음 코드는 컴파일되지 않습니다.

```java
class StrangeEmail extends UserEmail {
}
```

final class의 정확한 의미는 다음과 같습니다.

> 이 클래스를 부모로 사용하여 자식 클래스를 만들 수 없다.

다만 현재 UserEmail은 생성자도 private입니다.

```java
private UserEmail(String value) {
    this.value = value;
}
```

자식 클래스는 부모 생성자를 호출해야 하는데 private 생성자에는 접근할 수 없으므로, 사실 생성자만 보더라도 외부 상속이 불가
능합니다.

따라서 현재 코드에서 final은 꼭 있어야만 상속을 막을 수 있는 유일한 장치는 아닙니다. 다음 설계 의도를 더 명확하게 표현하는
역할이 큽니다.

> UserEmail은 상속해서 확장할 클래스가 아니라, 검증된 이메일 값을 나타내는 완성된 불변 객체다.

간단히 정리하면:

```text
final 필드 → 해당 필드에 다른 값을 다시 넣을 수 없음
final 메서드 → 자식 클래스가 해당 메서드를 재정의할 수 없음
final 클래스 → 해당 클래스를 상속할 수 없음
```

---

### private final String value

```java
private final String value;
```

- private: 외부에서 직접 접근하지 못함
- final: 생성 후 다른 값으로 바꿀 수 없음

따라서 생성된 UserEmail은 내부 이메일 값을 변경할 수 없습니다.

```java
UserEmail email =
        UserEmail.from("user@example.com");
```

값을 바꾸려면 새로운 객체를 만들어야 합니다.

이처럼 생성 후 내부 값이 바뀌지 않는 객체를 불변 객체라고 합니다.

---

### 생성자가 private인 이유

```java
private UserEmail(String value) {
    this.value = value;
}
```

외부에서는 다음 코드를 사용할 수 없습니다.

```java
new UserEmail("잘못된 값");
```

반드시 정적 팩토리 메서드를 거쳐야 합니다.

```java
UserEmail.from(rawEmail);
```

따라서 객체가 만들어지기 전에 항상 다음 작업이 실행됩니다.

```text
필수값 검사
→ 앞뒤 공백 제거
→ 소문자 변환
→ 길이 검사
→ 이메일 형식 검사
→ UserEmail 생성
```

---

### 정적 팩토리 메서드

```java
public static UserEmail from(String rawEmail)
```

정적 팩토리 메서드는 생성자 대신 객체를 만들어 주는 메서드입니다.

```java
UserEmail.from("User@Example.com");
```

from이라는 이름을 통해 원래 문자열을 UserEmail로 변환한다는 의도가 드러납니다.

```text
String
→ 검사와 정규화
→ UserEmail
```

---

### 유효한 상태만 존재

다음 값은 객체 생성 과정에서 거부됩니다.

```java
UserEmail.from(null);
UserEmail.from("");
UserEmail.from("invalid-email");
```

따라서 UserEmail 객체가 존재한다면 다음 조건을 만족한다고 기대할 수 있습니다.

```text
비어 있지 않음
길이가 254자 이하
이메일 형식
앞뒤 공백 제거
소문자 변환 완료
```

이것이 전용 값 객체를 사용하는 가장 중요한 이유입니다.

> 잘못된 이메일이 애플리케이션 안을 돌아다니지 못하게 합니다.

---

### Username 구조

```java
public final class Username {

    private final String value;
    private final String normalized;

    private Username(
            String value,
            String normalized
    ) {
        this.value = value;
        this.normalized = normalized;
    }
}
```

Username은 두 값을 가집니다.

```text
value      : 사용자에게 보여주는 이름
normalized : 중복 검사에 사용하는 이름
```

예를 들어:

```java
Username username =
        Username.from("  Test_User  ");
```

결과는 다음과 같습니다.

```text
value      = "Test_User"
normalized = "test_user"
```

---

### 왜 두 값을 보관할까?

화면에서는 사용자가 입력한 대소문자를 유지하고 싶습니다.

```text
Test_User
```

하지만 중복 검사는 대소문자를 무시해야 합니다.

```text
Test_User
test_user
TEST_USER
```

세 값이 모두 가입 가능하면 사용자와 URL을 구분하기 어려워집니다.

따라서 DB에는 다음 두 값을 저장합니다.

```text
username            = "Test_User"
username_normalized = "test_user"
```

그리고 username_normalized에 Unique Constraint를 적용합니다.

---

### Entity와 값 객체의 차이

User는 Entity입니다.

```java
@Entity
public class User {
    @Id
    private Long id;
}
```

DB에서 같은 사용자인지는 id로 구분합니다.

반면 UserEmail과 Username에는 다음이 없습니다.

```text
@Entity 없음
@Id 없음
별도 테이블 없음
```

회원가입 과정에서 값을 검사하고 정리하는 데 사용된 뒤, 내부 문자열 값이 User Entity에 저장됩니다.

```java
this.email = email.value();
this.username = username.value();
this.usernameNormalized = username.normalized();
```

즉:

```text
UserEmail·Username
→ 값을 안전하게 만들고 전달

User
→ 그 값을 DB에 저장하고 계정 상태를 관리
```

### 한 줄 요약

> UserEmail과 Username은 문자열을 의미 있는 타입으로 감싸고, 검사와 정규화를 반드시 거친 유효한 값만 애플리케이션 내부에서 사용하게 합니다.

<!-- 학습 원문 끝: lesson-03 -->

</details>

---

<a id="lesson-04"></a>

## 1.4 비밀번호를 안전하게 저장하는 방법

비밀번호는 원문 대신 **BCrypt 해시**를 저장합니다. 로그인에서는 입력값과 저장된 해시를 `matches()`에 전달합니다.

```java
String encodedPassword = passwordEncoder.encode(rawPassword);
boolean matches = passwordEncoder.matches(rawPassword, encodedPassword);
```

BCrypt 저장 문자열에는 Salt와 계산 비용 정보가 들어 있습니다. matches()가 그 정보를 이용하므로 새 Salt로 해싱한 문자열끼리 비교할 필요가 없습니다. 현재 정책은 최소 8자·UTF-8 최대 72바이트입니다.

<details>
<summary>상세히 보기: Salt 때문에 해시가 달라지는데 로그인에서는 어떻게 비교할까?</summary>

<!-- 학습 원문 시작: lesson-04 -->

회원가입에서 사용자가 입력한 비밀번호는 다음과 같습니다.

```text
password123!
```

이 값을 그대로 DB에 저장하면 안 됩니다.

```text
users
----------------------------------------
email              encoded_password
user@example.com   password123!   ← 위험
```

DB가 노출되면 사용자의 비밀번호도 그대로 보이기 때문입니다.

---

### OnFilm의 비밀번호 처리

AuthService에서는 다음 코드를 실행합니다.

```java
String encodedPassword =
        passwordEncoder.encode(rawPassword);
```

PasswordEncoder의 실제 구현은 BCryptPasswordEncoder입니다.

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

따라서 다음처럼 처리됩니다.

```text
입력한 비밀번호
password123!
        ↓ BCrypt
$2a$10$J7bQ...생략...
        ↓
DB 저장
```

---

### 정확히는 암호화보다 해싱

앞에서는 이해하기 쉽게 비밀번호를 암호화한다고 표현했지만, 정확하게는 BCrypt로 해싱한다고 표현하는 것이 좋습니다.

#### 암호화

암호화된 값은 열쇠를 사용해 원래 값으로 복구할 수 있습니다.

```text
원문
→ 암호화
→ 암호문
→ 복호화
→ 원문
```

#### 해싱

해시값에서는 원래 비밀번호를 다시 꺼내는 것을 전제로 하지 않습니다.

```text
원문 비밀번호
→ 해싱
→ 해시값

해시값
→ 원문 복구 불가
```

따라서 OnFilm 서버도 사용자의 원래 비밀번호를 알아낼 수 없습니다.

변수 이름은 encodedPassword이지만 실제 동작은 BCrypt 해싱입니다.

---

### BCrypt를 사용하는 이유

BCrypt는 비밀번호 저장을 위해 만들어진 해시 방식입니다.

#### 단방향 처리

저장된 해시값을 이용해 원래 비밀번호를 복구하지 않습니다.

#### Salt 자동 적용

같은 비밀번호를 여러 번 해싱해도 결과가 달라집니다.

```java
passwordEncoder.encode("password123!");
passwordEncoder.encode("password123!");
```

결과 예시:

```text
$2a$10$abc...첫 번째 결과
$2a$10$xyz...두 번째 결과
```

두 사용자가 같은 비밀번호를 사용해도 DB에는 서로 다른 값이 저장됩니다.

이때 추가되는 무작위 값을 Salt라고 합니다.

Salt가 없다면 같은 비밀번호는 항상 같은 해시값이 나와, 어떤 사용자들이 같은 비밀번호를 쓰는지 알아낼 수 있습니다.

#### 계산 비용

BCrypt는 일부러 계산하는 데 비용이 들도록 설계됐습니다.

공격자가 수많은 비밀번호를 빠르게 대입하는 것을 어렵게 만듭니다.

---

### 로그인할 때는 어떻게 비교할까?

원래 비밀번호를 복구해서 비교하지 않습니다.

로그인 시 사용자가 다시 입력한 비밀번호와 DB의 해시값을 matches()에 전달합니다.

```java
if (!passwordEncoder.matches(
        request.password(),
        user.encodedPassword()
)) {
    throw new InvalidCredentialsException();
}
```

예시는 다음과 같습니다.

사용자 입력:
password123!

DB 저장값:
$2a$10$J7bQ...

```java
BCrypt matches()
→ 입력값을 저장된 BCrypt 정보로 검증
→ 일치하면 true
```

로그인 전체 비교 과정은 다음과 같습니다.

```text
사용자가 비밀번호 입력
        ↓
DB에서 사용자의 비밀번호 해시 조회
        ↓
passwordEncoder.matches(입력값, 저장된 해시)
        ↓
true  → 로그인 진행
false → 로그인 실패
```

---

### Salt는 별도 컬럼에 저장하지 않아도 되나?

BCrypt 결과 안에 Salt와 비용 정보가 함께 포함됩니다.

```text
$2a$10$...
 │  │
 │  └─ 비용 설정
 └──── BCrypt 버전
```

따라서 OnFilm에서는 Salt를 별도 DB 컬럼으로 관리하지 않습니다.

matches()가 저장된 BCrypt 문자열에서 필요한 정보를 읽어 검증합니다.

---

### 비밀번호를 로그에 남기면 안 됨

해싱하기 전 비밀번호는 잠시 자바의 String으로 존재합니다.

```java
String rawPassword =
        RawPasswordPolicy.validate(request.password());
```

이 값을 다음처럼 로그로 남기면 안 됩니다.

```java
log.info("password={}", rawPassword); // 금지
```

요청 DTO 전체를 출력하는 것도 위험합니다.

```java
log.info("signup={}", request); // 비밀번호 포함 가능
```

비밀번호 원문은 다음 용도로만 사용해야 합니다.

```text
회원가입 → 해싱
로그인   → 저장된 해시와 비교
```

---

### BCrypt의 72바이트 제한

OnFilm에서는 비밀번호를 다음과 같이 검사합니다.

```text
rawPassword
    .getBytes(StandardCharsets.UTF_8)
    .length > 72
```

BCrypt는 입력 비밀번호를 72바이트까지만 처리하는 특성이 있기 때문입니다.

```text
영문 72자      → 일반적으로 72바이트
한글 24자      → UTF-8에서 일반적으로 72바이트
한글 25자      → 일반적으로 75바이트, 거부
```

문자 수만 확인하지 않고 UTF-8 바이트 수를 검사하는 이유입니다.

---

### PasswordEncoder는 어떻게 AuthService에 들어올까?

SecurityConfig에서 Spring Bean을 만듭니다.

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

AuthService는 인터페이스 타입으로 받습니다.

```java
private final PasswordEncoder passwordEncoder;
```

Spring이 만들어 둔 BCryptPasswordEncoder를 AuthService에 주입합니다.

```text
SecurityConfig
→ BCryptPasswordEncoder 생성
→ Spring이 Bean으로 보관
→ AuthService에 주입
```

AuthService는 구체적인 BCrypt 클래스가 아니라 PasswordEncoder 인터페이스에 의존합니다.

---

### 한 줄 요약

> OnFilm은 비밀번호 원문을 저장하지 않고 BCrypt로 단방향 해싱하며, 로그인할 때는 원문을 복구하지 않고 matches()로 사용자의 입력과 저장된 해시가 일치하는지 검증합니다.

<!-- 학습 원문 끝: lesson-04 -->

</details>

---

<a id="lesson-05"></a>

## 1.5 이메일·사용자 이름 중복 검사

첫 조회는 중복 계정의 불필요한 BCrypt 계산을 막습니다. 저장 직전 조회는 그동안 달라진 상태를 다시 확인합니다. **동시 요청의 중복 저장은 DB Unique Constraint가 최종 차단**합니다.

```java
transactionService.validateSignupAvailability(email, username);
String encodedPassword = passwordEncoder.encode(rawPassword);
transactionService.register(email, encodedPassword, username);
```

두 요청이 모두 ‘중복 없음’을 조회한 뒤 저장할 수 있기 때문입니다. register()는 saveAndFlush()로 DB 오류를 확인하고 제약조건 이름에 따라 중복 이메일·사용자 이름 오류로 바꿉니다.

<details>
<summary>상세히 보기: 중복을 두 번 검사해도 DB Unique Constraint가 필요한 이유</summary>

<!-- 학습 원문 시작: lesson-05 -->

OnFilm은 회원가입 중복을 두 단계로 확인합니다.

1. 애플리케이션에서 미리 조회
2. DB Unique Constraint로 최종 차단

두 검사는 서로 대체 관계가 아니라 역할이 다릅니다.

---

### 1. 애플리케이션에서 먼저 확인

```java
private void validateAvailability(
        UserEmail email,
        Username username
) {
    if (userRepository.existsByEmail(email.value())) {
        throw new DuplicateEmailException();
    }

    if (userRepository.existsByUsernameNormalized(
            username.normalized()
    )) {
        throw new DuplicateUsernameException();
    }
}
```

Repository는 다음 쿼리와 비슷한 검사를 실행합니다.

```sql
SELECT EXISTS(
    SELECT 1
    FROM users
    WHERE email = ?
);
```

이미 존재한다면 회원가입을 중단합니다.

```text
이메일 중복
→ DuplicateEmailException
→ HTTP 409 Conflict

사용자 이름 중복
→ DuplicateUsernameException
→ HTTP 409 Conflict
```

---

### 왜 저장 전에 미리 검사할까?

사용자에게 중복 원인을 분명하게 알려주기 위해서입니다.

```json
{
  "code": "DUPLICATE_EMAIL",
  "message": "이미 사용 중인 이메일입니다.",
  "errors": []
}
```

또한 BCrypt 해싱은 일부러 계산 비용이 들도록 만든 작업입니다.

OnFilm은 먼저 중복을 확인한 후 비밀번호를 해싱합니다.

```java
transactionService.validateSignupAvailability(
    email,
    username
);

String encodedPassword =
        passwordEncoder.encode(rawPassword);
```

중복된 계정이라면 불필요한 BCrypt 계산을 하지 않습니다.

---

### AuthService에서 한 번, 저장 직전에 한 번 검사

OnFilm 코드에는 중복 확인이 두 번 있습니다.

#### 첫 번째 검사

```java
transactionService.validateSignupAvailability(
    email,
    username
);
```

비밀번호를 해싱하기 전에 빠르게 중복을 확인합니다.

#### 두 번째 검사

```java
@Transactional
public void register(...) {
    validateAvailability(email, username);

    User user = User.create(...);
    user.createPerson(...);

    userRepository.saveAndFlush(user);
}
```

실제 저장 트랜잭션 안에서 다시 확인합니다.

두 검사 사이에는 BCrypt 계산 시간이 있습니다.

```text
첫 번째 중복 확인
→ BCrypt 계산
→ 두 번째 중복 확인
→ User 저장
```

두 번째 검사는 저장에 더 가까운 시점에서 상태를 다시 확인하는 역할을 합니다.

———
- 보충설명
핵심은 BCrypt 계산을 하는 동안 다른 회원가입 요청이 먼저 저장될 수 있기 때문입니다.

현재 회원가입 흐름은 두 트랜잭션으로 나뉩니다.

```text
첫 번째 조회 트랜잭션
→ 중복 확인
→ 트랜잭션 종료

BCrypt 계산
→ DB 트랜잭션 밖에서 수행

두 번째 저장 트랜잭션
→ 중복 재확인
→ User 저장
→ Commit
```

### 첫 번째 검사의 목적

```java
transactionService.validateSignupAvailability(
    email,
    username
);
```

이미 가입된 이메일이라면 비밀번호를 해싱할 필요가 없습니다.

```text
중복 이메일 입력
→ 첫 번째 검사에서 중복 발견
→ BCrypt 계산하지 않음
→ 바로 409 응답
```

BCrypt는 보안을 위해 일부러 계산이 느리도록 만들어졌기 때문에, 불필요한 계산을 피하는 것입니다.

---

### 왜 BCrypt를 트랜잭션 밖에서 실행할까?

BCrypt 계산은 DB 작업이 아니라 CPU 작업입니다.

```text
DB 중복 확인
→ BCrypt 계산
→ DB 저장
```

이 모든 과정을 하나의 트랜잭션으로 묶으면 BCrypt를 계산하는 동안에도 DB 트랜잭션이 열려 있게 됩니다.

```text
DB 트랜잭션 시작
→ 중복 조회
→ BCrypt 계산 동안 대기
→ 저장
→ 트랜잭션 종료
```

그러면 트랜잭션과 DB 연결을 필요 이상으로 오래 사용할 수 있습니다.

그래서 다음처럼 분리했습니다.

```text
중복 확인 트랜잭션 종료
→ BCrypt 계산
→ 저장 트랜잭션 시작
```

---

### 두 번째 검사의 목적

첫 번째 검사가 끝나고 BCrypt를 계산하는 동안 다른 사용자가 같은 이메일을 먼저 저장할 수 있습니다.

예를 들어 요청 A와 B가 같은 이메일로 가입한다고 해보겠습니다.

```text
요청 A: 첫 번째 중복 확인 → 없음
요청 A: BCrypt 계산 시작
```

요청 B: 회원가입 및 저장 완료

요청 A: BCrypt 계산 완료

요청 A는 처음 확인할 때 중복이 없었지만, BCrypt를 계산하는 사이 요청 B가 같은 이메일을 저장했습니다.

그래서 실제 저장 트랜잭션이 시작된 후 다시 검사합니다.

```java
@Transactional
public void register(...) {
    validateAvailability(email, username); // 재확인

    User user = User.create(...);
    user.createPerson(...);

    userRepository.saveAndFlush(user);
}
```

```text
요청 A: 두 번째 중복 확인
→ 요청 B가 저장한 이메일 발견
→ 저장하지 않고 중복 오류 반환
```

즉, 두 번째 검사는 첫 번째 검사 이후 상황이 달라졌는지 확인하는 것입니다.

---

### 두 번째 검사로도 완벽하지는 않음

두 번째 검사 직후에도 다른 요청이 먼저 저장할 가능성이 있습니다.

```text
요청 A: 두 번째 검사 → 없음
요청 B: 두 번째 검사 → 없음

요청 A: 저장 성공
요청 B: 저장 시도
```

그래서 최종적으로 DB의 Unique Constraint가 필요합니다.

```text
요청 A → 저장 성공
요청 B → DB Unique Constraint 위반
```

### 세 가지 역할

```text
첫 번째 검사
→ 중복이면 비싼 BCrypt 계산을 하지 않음

두 번째 검사
→ BCrypt 계산 중 생긴 중복을 저장 직전에 다시 확인

DB Unique Constraint
→ 두 번째 검사 직후의 동시 요청까지 최종 차단
```

다만 데이터 중복을 실제로 보장하는 핵심은 DB Unique Constraint입니다. 첫 번째와 두 번째 조회 검사는 빠르고 이해하기 좋은 오
류 처리를 위한 보조 장치입니다.

한 줄로 정리하면:

> 첫 번째 검사는 불필요한 BCrypt 계산을 막고, 두 번째 검사는 그 계산 중 달라진 DB 상태를 다시 확인하며, 최종 동시 요청은 DB
> Unique Constraint가 차단합니다.

---

### 조회 검사만으로는 부족한 이유

동시에 두 사용자가 같은 이메일로 가입한다고 가정하겠습니다.

```text
요청 A: same@example.com
요청 B: same@example.com
```

실행 순서가 다음처럼 될 수 있습니다.

```text
요청 A → 이메일 조회 → 없음
요청 B → 이메일 조회 → 없음

요청 A → 저장 시도
요청 B → 저장 시도
```

두 요청 모두 조회 시점에는 이메일이 없다고 확인했습니다.

이를 동시성 문제 또는 Race Condition이라고 합니다.

```text
조회할 때는 없었지만
저장하기 전 다른 요청이 먼저 저장할 수 있음
```

애플리케이션에서 두 번 확인해도 검사와 저장 사이의 아주 짧은 시간 때문에 가능성을 완전히 없애지는 못합니다.

---

### DB Unique Constraint

그래서 users 테이블에 Unique Constraint를 적용합니다.

```java
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_users_email",
            columnNames = "email"
        ),
        @UniqueConstraint(
            name = "uk_users_username_normalized",
            columnNames = "username_normalized"
        )
    }
)
```

DB 규칙은 다음과 같습니다.

```text
email
→ 같은 값을 두 행에 저장할 수 없음

username_normalized
→ 같은 값을 두 행에 저장할 수 없음
```

동시에 두 요청이 저장을 시도하면:

```text
요청 A → INSERT 성공
요청 B → DB Unique Constraint 위반
```

최종적으로 DB에는 한 명만 저장됩니다.

---

### 사용자 이름은 normalized 값에 적용

다음 두 이름을 생각해 보겠습니다.

```java
TestUser
testuser
```

원본 문자열은 다르지만 OnFilm에서는 같은 사용자 이름으로 취급합니다.

```text
TestUser → testuser
testuser → testuser
```

그래서 화면용 username이 아니라 비교용 username_normalized에 Unique Constraint를 적용합니다.

```text
username            username_normalized
TestUser             testuser

새 가입 시도
testuser             testuser
                     ↑ 중복이므로 DB가 거부
```

---

### saveAndFlush()를 사용하는 이유

```java
try {
    userRepository.saveAndFlush(user);
} catch (DataIntegrityViolationException exception) {
    throw translateDuplicateUserException(exception);
}
```

단순 save()는 INSERT SQL 실행이 트랜잭션이 끝날 때까지 미뤄질 수 있습니다.

그러면 DB 예외가 try-catch 바깥에서 발생할 가능성이 있습니다.

saveAndFlush()는 이 메서드 안에서 INSERT SQL을 DB에 보내도록 합니다.

```text
saveAndFlush()
→ INSERT 실행
→ Unique Constraint 확인
→ 위반하면 이 위치에서 예외 발생
```

따라서 DB 예외를 OnFilm 오류로 변환할 수 있습니다.

```text
uk_users_email 위반
→ DuplicateEmailException

uk_users_username_normalized 위반
→ DuplicateUsernameException
```

---

### 왜 애플리케이션 검사와 DB 제약조건을 모두 사용할까?

### 애플리케이션 검사

```text
역할: 중복을 미리 확인
장점: 사용자에게 명확한 오류 제공
한계: 동시 요청을 완전히 막지 못함
```

### DB Unique Constraint

```text
역할: 중복 저장을 최종 차단
장점: 동시 요청에도 데이터 무결성 보장
한계: 발생한 DB 오류를 사용자용 오류로 변환해야 함
```

둘을 함께 사용하면:

```text
일반 중복 요청
→ 애플리케이션에서 빠르게 차단

동시에 들어온 중복 요청
→ DB가 최종 차단
```

### 한 줄 요약

> OnFilm은 애플리케이션 조회로 중복을 미리 안내하고, 동시에 들어온 요청까지 안전하게 막기 위해 DB Unique Constraint를 최종 방어선으로 사용합니다.

<!-- 학습 원문 끝: lesson-05 -->

</details>

---

<a id="lesson-06"></a>

## 1.6 User와 기본 Person 생성

회원가입 시 로그인 계정 User와 기본 프로필 Person을 일대일로 연결합니다. **User 저장에 Person 저장이 전파되고, 두 저장은 함께 커밋하거나 롤백**됩니다.

```java
User user = User.create(email, encodedPassword, username);
user.createPerson(username.value());
userRepository.saveAndFlush(user);
```

Cascade는 JPA 작업 전파이며 repository.save()를 직접 대신 호출한다는 뜻은 아닙니다. Flush는 Commit이 아닙니다. 현재 IDENTITY 방식에서는 save() 자체도 INSERT를 실행할 수 있으므로 실행 시점을 단정하지 않습니다.

<details>
<summary>상세히 보기: require·기본 Person 생성·Cascade·saveAndFlush와 롤백</summary>

<!-- 학습 원문 시작: lesson-06 -->

회원가입에서는 로그인 계정인 User만 만드는 것이 아니라 기본 프로필인 Person도 함께 만듭니다.

```java
User user = User.create(
    email,
    encodedPassword,
    username
);

user.createPerson(username.value());
```

결과는 다음과 같습니다.

```text
User
├─ 로그인 이메일
├─ 비밀번호 해시
├─ 사용자 이름
└─ Person ── 기본 프로필
```

---

### 1. User 생성

```java
public static User create(
        UserEmail email,
        String encodedPassword,
        Username username
) {
    return new User(
        email,
        encodedPassword,
        username
    );
}
```

실제 생성자는 private입니다.

```java
private User(
        UserEmail email,
        String encodedPassword,
        Username username
) {
    UserEmail requiredEmail =
            require(email, "email");

    Username requiredUsername =
            require(username, "username");

    this.email = requiredEmail.value();
    this.encodedPassword =
            requireEncodedPassword(encodedPassword);

    this.username =
            requiredUsername.value();

    this.usernameNormalized =
            requiredUsername.normalized();
}
```

UserEmail과 Username 객체에 들어 있던 값을 꺼내 User에 저장합니다.

```text
UserEmail.value()
→ email

Username.value()
→ username

Username.normalized()
→ usernameNormalized
```

---

### 왜 생성자를 직접 사용하지 않을까?

외부 코드에서는 다음과 같이 만들 수 없습니다.

```java
new User(...); // private이므로 불가능
```

반드시 다음 메서드를 사용합니다.

```java
User.create(...);
```

이렇게 하면 객체를 만드는 방법을 한곳으로 제한할 수 있습니다.

```text
null 검사
비밀번호 해시 검사
이메일 값 설정
사용자 이름 설정
정규화된 이름 설정
```

또한 메서드 이름을 보고 새로운 사용자를 만든다는 의도를 알 수 있습니다.

---

### 2. 기본 Person 생성

User를 만든 다음 다음 메서드를 호출합니다.

```java
user.createPerson(username.value());
```

내부 코드는 다음과 같습니다.

```java
public Person createPerson(String name) {
    return createPerson(
        name,
        null,
        null,
        null,
        null,
        List.of(),
        List.of()
    );
}
```

처음 회원가입할 때는 사용자 이름만 프로필 이름으로 사용하고, 나머지는 초기값으로 둡니다.

```text
name             = username
birthDate        = null
birthPlace       = null
oneLineIntro     = null
profileImageKey  = null
snsList          = 빈 목록
profileTags      = 빈 목록
publicId         = 새 UUID
```

예를 들면:

회원가입 username = TestActor

기본 프로필은 다음처럼 만들어집니다.

```java
Person
name      = TestActor
publicId  = 421ebf23-70c6-451c-b45a-408d75da51f7
SNS       = []
태그       = []
```

사용자는 회원가입 후 프로필 수정 기능에서 나머지 정보를 추가할 수 있습니다.

---

### 왜 회원가입할 때 Person까지 만들까?

OnFilm의 사용자 계정은 항상 하나의 배우 프로필을 관리합니다.

만약 User만 먼저 만들면 다음과 같은 상태가 생길 수 있습니다.

```text
User는 존재
Person은 없음
```

프로필 조회나 작품 등록을 할 때마다 다음을 확인해야 합니다.

```java
if (user.getPerson() == null) {
    // 프로필이 없는 경우 처리
}
```

회원가입할 때 기본 Person을 함께 만들면 다음 조건을 유지할 수 있습니다.

> 가입된 모든 User는 하나의 Person을 가진다.

---

### 3. 이미 Person이 있으면 생성 차단

```java
if (this.person != null) {
    throw new IllegalStateException(
        "user already has a person"
    );
}
```

한 사용자가 createPerson()을 두 번 호출하면 예외가 발생합니다.

```java
user.createPerson("첫 번째 프로필");
user.createPerson("두 번째 프로필"); // 예외
```

현재 OnFilm의 관계는 다음과 같기 때문입니다.

```text
User 1명 → Person 1개
```

---

### 4. User와 Person 연결

새로운 Person을 만든 다음 attachPerson()을 호출합니다.

```java
Person created = Person.create(...);
attachPerson(created);
```

attachPerson()은 양쪽 객체를 모두 연결합니다.

```text
User.person → Person
Person.user → User
```

따라서 다음 두 방향으로 접근할 수 있습니다.

```java
user.getPerson();
person.getUser();
```

---

### 5. Cascade를 이용한 함께 저장

User에는 다음 설정이 있습니다.

```java
@OneToOne(
    fetch = FetchType.LAZY,
    optional = false,
    cascade = CascadeType.ALL,
    orphanRemoval = true
)
@JoinColumn(
    name = "person_id",
    nullable = false,
    unique = true
)
private Person person;
```

여기서 중요한 부분은 다음입니다.

```text
cascade = CascadeType.ALL
```

User에 수행한 저장 작업을 연결된 Person에도 전달합니다.

따라서 다음 코드 하나만 실행합니다.

```java
userRepository.saveAndFlush(user);
```

별도로 다음 코드를 실행하지 않아도 됩니다.

```java
personRepository.save(person);
userRepository.save(user);
```

JPA가 연결된 Person까지 함께 저장합니다.

---

### 실제 저장 흐름

users 테이블이 person_id를 가지고 있으므로, 먼저 참조 대상인 Person이 저장됩니다.

1. Person INSERT
2. 생성된 person_id 확인
3. User INSERT
4. users.person_id에 Person ID 저장

개념적인 SQL은 다음과 같습니다.

```sql
INSERT INTO person (
    name,
    public_id
) VALUES (
    'TestActor',
    '421ebf23-...'
);

INSERT INTO users (
    email,
    encoded_password,
    username,
    username_normalized,
    person_id
) VALUES (
    'user@example.com',
    '$2a$10$...',
    'TestActor',
    'testactor',
    10
);
```

---

### 6. 하나의 트랜잭션으로 저장

```java
@Transactional
public void register(...) {
    User user = User.create(...);
    user.createPerson(...);
    userRepository.saveAndFlush(user);
}
```

User와 Person 저장은 같은 트랜잭션에서 수행됩니다.

#### saveAndFlush()를 사용하는 이유

현재 코드는 다음과 같습니다.

```java
try {
    userRepository.saveAndFlush(user);
} catch (DataIntegrityViolationException exception) {
    throw translateDuplicateUserException(exception);
}
```

### save()만 사용하면

```java
userRepository.save(user);
```

새로운 Entity를 영속성 컨텍스트에 등록하지만, INSERT SQL은 트랜잭션을 커밋할 때까지 미뤄질 수 있습니다.

```text
save(user)
→ User를 영속성 컨텍스트에 등록
→ 아직 INSERT가 실행되지 않을 수 있음
→ register() 메서드 종료
→ Commit 직전에 Flush
→ INSERT 실행
→ DB 중복 오류 발생
```

문제는 트랜잭션 Commit이 register() 메서드가 반환된 뒤 Spring에 의해 처리된다는 점입니다.

따라서 DB 오류가 다음 try-catch를 벗어난 시점에 발생할 수 있습니다.

```java
try {
    userRepository.save(user);
    // 여기서는 아직 DB 오류가 없을 수 있음
} catch (DataIntegrityViolationException exception) {
    // 예외가 나중에 발생하면 잡지 못할 수 있음
}
```

---

### saveAndFlush()를 사용하면

```java
userRepository.saveAndFlush(user);
```

다음 두 작업을 연속으로 수행합니다.

```text
save
→ Entity를 영속성 컨텍스트에 등록

flush
→ INSERT SQL을 바로 DB에 전달
```

따라서 DB의 Unique Constraint 검사도 이 코드가 실행되는 동안 발생합니다.

```text
saveAndFlush(user)
→ INSERT 실행
→ 이메일 또는 username 중복 확인
→ DataIntegrityViolationException 발생
→ 현재 try-catch에서 예외 처리
```

그 결과 DB 예외를 사용자에게 의미 있는 OnFilm 예외로 바꿀 수 있습니다.

```text
uk_users_email 위반
→ DuplicateEmailException
→ HTTP 409 DUPLICATE_EMAIL

uk_users_username_normalized 위반
→ DuplicateUsernameException
→ HTTP 409 DUPLICATE_USERNAME
```

---

### saveAndFlush()가 Commit까지 하는 것은 아님

이 부분이 중요합니다.

```text
saveAndFlush
→ SQL을 DB에 전달
→ 아직 Commit은 아님
→ 문제가 생기면 Rollback 가능
```

회원가입 흐름은 다음과 같습니다.

```text
saveAndFlush()
→ Person INSERT
→ User INSERT
→ DB 제약조건 확인
→ 모두 정상
→ 메서드 종료
→ Commit
```

예외가 발생하면:

```text
saveAndFlush()
→ User INSERT 중 중복 오류
→ 예외 변환
→ 트랜잭션 Rollback
→ 앞에서 실행된 Person INSERT도 취소
```

---

### 항상 saveAndFlush()를 쓰면 좋은가?

아닙니다. 일반적인 저장에서는 save()로 충분합니다.

JPA가 적절한 시점에 SQL을 모아서 실행할 수 있는데, 매번 강제로 Flush하면 이런 장점을 줄일 수 있습니다.

```text
일반적인 신규 Entity 저장
→ save()

DB 오류를 현재 코드 위치에서 즉시 확인해야 함
→ saveAndFlush()
```

OnFilm 회원가입에서는 동시 가입으로 발생한 Unique Constraint 오류를 try-catch 안에서 확인해야 하므로 saveAndFlush()를 사용
했습니다.

한 줄로 정리하면:

> saveAndFlush()는 회원 정보를 즉시 커밋하려는 것이 아니라, INSERT SQL을 현재 try-catch 안에서 실행해 DB 중복 오류를 OnFilm의 중복 회원 오류로 변환하기 위해 사용했습니다.

#### 모두 성공

```text
Person INSERT 성공
→ User INSERT 성공
→ Commit
```

둘 다 DB에 남습니다.

#### User 저장 실패

```text
Person INSERT 실행
→ User의 이메일 중복으로 INSERT 실패
→ Rollback
```

앞에서 실행된 Person INSERT도 취소됩니다.

따라서 계정 없이 프로필만 남는 상태를 방지합니다.

---

### 7. 주요 JPA 설정

### @OneToOne

```java
@OneToOne
```

User 한 명과 Person 한 명이 연결됩니다.

### optional = false

```text
optional = false
```

User는 Person 없이 존재할 수 없다는 JPA 관계 의도를 나타냅니다.

### nullable = false

```java
@JoinColumn(nullable = false)
```

DB의 person_id에 NULL을 저장할 수 없습니다.

### unique = true

```java
@JoinColumn(unique = true)
```

하나의 Person을 여러 User가 공유하지 못하게 합니다.

```text
User A → Person 10
User B → Person 10  // DB에서 차단
```

### cascade = ALL

User를 저장하거나 삭제하는 작업을 연결된 Person에도 전달합니다.

### orphanRemoval = true

User와의 관계에서 제거된 Person을 고아 객체로 보고 삭제할 수 있게 합니다.

현재 관계에서는 Person이 User의 프로필로 생명주기를 함께한다는 의미가 있습니다.

---

### 전체 생성 흐름

```text
UserEmail, Username, 비밀번호 해시 준비
        ↓
User.create()
        ↓
User 객체 생성
        ↓
user.createPerson()
        ↓
Person 객체 생성
        ↓
User와 Person 양방향 연결
        ↓
userRepository.saveAndFlush(user)
        ↓
Cascade로 Person 저장
        ↓
User 저장
        ↓
Commit
```

### 한 줄 요약

> OnFilm은 회원가입 시 로그인 계정인 User와 기본 프로필인 Person을 일대일로 연결해 같은 트랜잭션에서 저장하며, Cascade를 사용해 User 저장만으로 두 객체를 함께 저장합니다.

<!-- 학습 원문 끝: lesson-06 -->

</details>

---

<a id="lesson-07"></a>

## 1.2 로그인 전체 흐름

로그인은 이메일로 계정을 찾고 **BCrypt로 비밀번호를 확인한 뒤 Access·Refresh Token을 발급**하는 기능입니다. 이메일 없음과 비밀번호 불일치는 같은 `401 INVALID_CREDENTIALS`로 응답합니다.

```java
if (!passwordEncoder.matches(request.password(), user.encodedPassword())) {
    throw invalidCredentials();
}
```

Controller는 Access·Refresh·CSRF 쿠키를 Set-Cookie로 보냅니다. 토큰은 응답 쿠키에 있으며, 현재 JSON 본문의 accessToken은 null입니다.

<details>
<summary>상세히 보기: 로그인 DTO·LoginSnapshot·비밀번호 비교·쿠키 응답</summary>

<!-- 학습 원문 시작: lesson-07 -->

회원가입을 마친 사용자는 이메일과 비밀번호로 로그인합니다.

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "email": "test3@onfilm.local",
  "password": "password123!"
}
```

전체 흐름은 다음과 같습니다.

```text
로그인 요청
→ LoginRequest 입력값 검사
→ 이메일 정규화
→ 이메일로 User 조회
→ 입력 비밀번호와 저장된 BCrypt 해시 비교
→ Access Token 생성
→ Refresh Token 생성 및 저장
→ Access·Refresh·CSRF 쿠키 생성
→ HTTP 200 응답
```

---

### 1. LoginRequest

```java
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = UserEmail.MAX_LENGTH)
        String email,

        @NotBlank
        String password
) {
}
```

Controller의 @Valid가 입력값을 검사합니다.

```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request
) {
    ...
}
```

#### 이메일

```java
@NotBlank
@Email
@Size(max = 254)
```

- 비어 있지 않아야 함
- 이메일 형식이어야 함
- 254자를 넘지 않아야 함

#### 비밀번호

```java
@NotBlank
```

비어 있지 않아야 합니다.

회원가입 때는 비밀번호 정책 전체를 검사했지만, 로그인에서는 입력값이 가입 정책을 만족하는지 다시 검사하지 않습니다.

로그인의 핵심은 다음이기 때문입니다.

> 입력한 비밀번호가 DB에 저장된 비밀번호 해시와 일치하는가?

---

### 2. Controller가 AuthService 호출

```java
AuthTokens tokens =
        authService.login(request);
```

AuthService.login()이 실제 인증 과정을 수행합니다.

```java
public AuthTokens login(LoginRequest request) {
    UserEmail email =
            UserEmail.from(request.email());

    AuthTransactionService.LoginSnapshot user =
            transactionService.findLoginSnapshot(email);

    if (!passwordEncoder.matches(
            request.password(),
            user.encodedPassword()
    )) {
        throw invalidCredentials();
    }

    String accessToken =
            jwtProvider.createAccessToken(
                user.userId(),
                authProperties.accessTokenTtl()
            );

    String refreshToken =
            refreshTokenService.issue(
                user.userId(),
                authProperties.refreshTokenTtl()
            );

    return new AuthTokens(
        accessToken,
        refreshToken
    );
}
```

---

### 3. 이메일 정규화

```java
UserEmail email =
        UserEmail.from(request.email());
```

로그인 이메일도 회원가입 때와 같은 방식으로 정리합니다.

```text
" Test3@OnFilm.local "
→ "test3@onfilm.local"
```

회원가입 때 소문자로 저장했으므로 로그인할 때도 같은 기준으로 비교해야 합니다.

```text
회원가입 저장값 : test3@onfilm.local
로그인 입력값   : Test3@OnFilm.local
정규화 결과     : test3@onfilm.local
→ 같은 이메일로 조회
```

---

### 4. 이메일로 사용자 조회

```java
AuthTransactionService.LoginSnapshot user =
        transactionService.findLoginSnapshot(email);
```

내부에서는 다음과 같이 조회합니다.

```java
@Transactional(readOnly = true)
public LoginSnapshot findLoginSnapshot(
        UserEmail email
) {
    User user = userRepository
            .findByEmail(email.value())
            .orElseThrow(
                InvalidCredentialsException::new
            );

    return new LoginSnapshot(
        user.getId(),
        user.getEncodedPassword()
    );
}
```

로그인 검증에는 User 전체 정보가 필요하지 않습니다.

필요한 값은 두 가지입니다.

```text
userId          → 토큰에 사용자 식별값으로 사용
encodedPassword → 입력 비밀번호와 비교
```

그래서 다음 작은 객체로 반환합니다.

```java
public record LoginSnapshot(
    Long userId,
    String encodedPassword
) {
}
```

---

### 5. 비밀번호 비교

```java
passwordEncoder.matches(
    request.password(),
    user.encodedPassword()
);
```

첫 번째 값은 사용자가 로그인할 때 입력한 원문입니다.

```text
password123!
```

두 번째 값은 DB에 저장된 BCrypt 해시입니다.

```text
$2a$10$J7bQ...
```

matches()가 저장된 BCrypt 문자열에서 Salt와 계산 정보를 읽어 입력값을 검증합니다.

```text
일치   → true
불일치 → false
```

불일치하면 다음 예외가 발생합니다.

```java
throw new InvalidCredentialsException();
```

응답은 다음과 같습니다.

HTTP 401 Unauthorized
INVALID_CREDENTIALS
이메일 또는 비밀번호가 올바르지 않습니다.

---

### 이메일이 없어도 같은 오류를 반환

이메일로 사용자를 찾지 못했을 때도:

```java
.orElseThrow(
    InvalidCredentialsException::new
);
```

비밀번호가 틀렸을 때도:

```java
throw new InvalidCredentialsException();
```

같은 오류를 반환합니다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "errors": []
}
```

다음처럼 구분해서 알려주지 않습니다.

등록되지 않은 이메일입니다.
비밀번호가 틀렸습니다.

이유는 공격자에게 특정 이메일의 가입 여부를 쉽게 알려주지 않기 위해서입니다.

---

### 6. Access Token 생성

비밀번호가 일치하면 Access Token을 만듭니다.

```java
String accessToken =
        jwtProvider.createAccessToken(
            user.userId(),
            authProperties.accessTokenTtl()
        );
```

Access Token에는 로그인한 사용자를 식별할 수 있는 userId와 만료 시간 등이 들어갑니다.

```text
Access Token
├─ userId
├─ 발급 시각
├─ 만료 시각
└─ 서버 서명
```

이후 사용자가 인증이 필요한 API를 호출할 때 사용됩니다.

```text
프로필 수정
작품 등록
스토리보드 수정
영상 업로드
```

---

### 7. Refresh Token 생성

```java
String refreshToken =
        refreshTokenService.issue(
            user.userId(),
            authProperties.refreshTokenTtl()
        );
```

Refresh Token은 Access Token이 만료됐을 때 새로운 토큰을 발급받기 위해 사용합니다.

```text
Access Token
→ API 요청 인증
→ 비교적 짧은 수명

Refresh Token
→ Access Token 재발급
→ 상대적으로 긴 수명
```

Refresh Token은 발급 기록을 DB에 저장해 폐기와 재사용 탐지를 관리합니다. 자세한 구조는 토큰 관리 단원에서 살펴봅니다.

---

### 8. AuthTokens로 두 토큰 반환

```java
public record AuthTokens(
    String accessToken,
    String refreshToken
) {
}
```

AuthService는 생성한 두 토큰을 Controller에 반환합니다.

```java
return new AuthTokens(
    accessToken,
    refreshToken
);
```

이 객체는 최종 사용자 응답이 아니라 Service와 Controller 사이에서 두 토큰을 전달하는 내부 DTO입니다.

---

### 9. 세 종류의 쿠키 생성

Controller는 다음 쿠키를 만듭니다.

```java
ResponseCookie accessCookie =
        authCookieFactory.createAccessCookie(
            tokens.accessToken()
        );

ResponseCookie csrfCookie =
        authCookieFactory.createCsrfCookie();

ResponseCookie refreshCookie =
        authCookieFactory.createRefreshCookie(
            tokens.refreshToken()
        );
```

#### Access Token 쿠키

```text
이름: access_token
용도: 일반 API 요청 인증
```

#### Refresh Token 쿠키

```text
이름: refresh_token
용도: Access Token 재발급
```

#### CSRF Token 쿠키

```text
이름: XSRF-TOKEN
용도: 다른 사이트가 사용자의 쿠키를 악용하는 요청 방지
```

각 쿠키의 보안 옵션은 이후 인증·보안 부분에서 자세히 살펴봅니다.

---

### 10. HTTP 응답

```java
return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            accessCookie.toString()
        )
        .header(
            HttpHeaders.SET_COOKIE,
            csrfCookie.toString()
        )
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie.toString()
        )
        .body(new AuthResponse(null));
```

서버는 Set-Cookie 응답 헤더로 세 쿠키를 브라우저에 전달합니다.

```http
Set-Cookie: access_token=...
Set-Cookie: XSRF-TOKEN=...
Set-Cookie: refresh_token=...
```

현재 구현에서는 Access Token을 JSON 본문에 넣지 않고 쿠키로 전달하므로 응답 본문은 다음과 같습니다.

```json
{
  "accessToken": null
}
```

로그인 성공 상태는 HTTP 200 OK입니다.

---

### 전체 흐름

```http
POST /auth/login
        ↓
+LoginRequest 검증
        ↓
+AuthService.login()
        ├─ 이메일 정규화
        ├─ 이메일로 User 조회
        └─ BCrypt 비밀번호 비교
        ↓
+비밀번호 일치
        ↓
+Access Token 생성
        ↓
+Refresh Token 생성·저장
        ↓
+AuthTokens를 Controller에 반환
        ↓
+Access·Refresh·CSRF 쿠키 생성
        ↓
+Set-Cookie 헤더로 브라우저에 전달
        ↓
+HTTP 200 OK
```

### 한 줄 요약

> OnFilm 로그인은 이메일로 사용자를 찾고 BCrypt로 비밀번호를 검증한 뒤, Access Token과 Refresh Token을 발급해 보안 쿠키로 브라우저에 전달합니다.

<!-- 학습 원문 끝: lesson-07 -->

</details>

---

<a id="lesson-08"></a>

## 1.2-1 Access Token과 JWT 인증 흐름

Access JWT에는 사용자 ID와 만료 시각 등이 들어 있습니다. 요청마다 서명과 만료를 확인하고 **현재 요청의 인증 정보를 SecurityContext에 등록**합니다.

```java
if (token != null && !token.isBlank() && jwtProvider.validate(token)) {
    Authentication authentication = jwtProvider.getAuthentication(token);
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

Bearer 헤더가 있으면 우선 사용하고 아니면 쿠키를 찾습니다. 현재 서명된 JWT의 내용은 읽을 수 있으므로 비밀값을 넣으면 안 됩니다. STATELESS는 HttpSession에 로그인 상태를 유지하지 않는다는 뜻입니다.

<details>
<summary>상세히 보기: JWT 서명·Bearer 헤더·쿠키·Authentication·SecurityContext</summary>

<!-- 학습 원문 시작: lesson-08 -->

로그인에 성공하면 다음 코드로 Access Token을 만듭니다.

```java
String accessToken =
        jwtProvider.createAccessToken(
            user.userId(),
            authProperties.accessTokenTtl()
        );
```

OnFilm의 Access Token은 JWT 형식입니다.

---

### JWT란?

JWT는 서버가 사용자 정보와 만료 시간을 담고 서명한 문자열입니다.

형태는 점을 기준으로 세 부분으로 나뉩니다.

```text
Header.Payload.Signature
```

예시는 다음과 같습니다.

```java
eyJhbGciOiJIUzI1NiJ9
.
eyJzdWIiOiIxIiwiaWF0Ijox...}
.
서명값
```

각 부분의 역할은 다음과 같습니다.

```text
Header
→ 사용한 서명 알고리즘 등의 정보

Payload
→ userId, 발급 시각, 만료 시각

Signature
→ 토큰이 서버가 발급한 것이며 변조되지 않았는지 확인
```

---

### Access Token 생성 코드

```java
public String createAccessToken(
        Long userId,
        Duration ttl
) {
    Instant now = Instant.now();

    return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
}
```

#### subject

```java
.subject(String.valueOf(userId))
```

JWT의 sub 필드에 사용자 ID를 저장합니다.

```json
{
  "sub": "10"
}
```

OnFilm은 이후 이 값을 이용해 현재 로그인한 사용자를 찾습니다.

#### issuedAt

```java
.issuedAt(Date.from(now))
```

토큰을 발급한 시각입니다.

JWT에서는 일반적으로 iat로 표현합니다.

#### expiration

```java
.expiration(Date.from(now.plus(ttl)))
```

토큰이 만료되는 시각입니다.

JWT에서는 일반적으로 exp로 표현합니다.

#### signWith

```java
.signWith(key)
```

서버의 비밀키로 토큰에 서명합니다.

누군가 Payload의 사용자 ID를 임의로 바꾸면 기존 서명과 맞지 않으므로 검증에 실패합니다.

---

### JWT는 암호화된 데이터가 아님

JWT의 Payload는 Base64 URL 형식으로 표현되지만 암호화된 것이 아닙니다.

토큰을 가진 사람은 Payload 내용을 확인할 수 있습니다.

```json
{
  "sub": "10",
  "iat": 1780000000,
  "exp": 1780000900
}
```

따라서 JWT에 다음과 같은 민감한 정보를 넣으면 안 됩니다.

```text
비밀번호
주민등록번호
카드 번호
Refresh Token
```

OnFilm은 사용자 식별에 필요한 userId와 시간 정보만 넣습니다.

서명은 내용을 숨기는 기능이 아니라 다음을 확인하는 기능입니다.

> 토큰이 서버가 만든 것이고, 발급 후 내용이 바뀌지 않았는가?

---

### 로그인 이후 API 요청

브라우저는 로그인 응답에서 받은 access_token 쿠키를 저장합니다.

이후 프로필 수정 API를 호출한다고 가정하겠습니다.

```http
PUT /api/people/{publicId}
Cookie: access_token=eyJhbGci...
```

요청은 Controller로 바로 들어가지 않고 먼저 Spring Security Filter를 통과합니다.

```text
브라우저 요청
→ JwtAuthFilter
→ 토큰 확인과 검증
→ 로그인 정보 등록
→ 권한 검사
→ Controller
```

---

### JwtAuthFilter

```java
public class JwtAuthFilter
        extends OncePerRequestFilter {
}
```

OncePerRequestFilter를 상속했기 때문에 HTTP 요청마다 한 번 실행됩니다.

---

### 1. Authorization 헤더 확인

```java
String auth =
        request.getHeader("Authorization");
```

다음 형식을 지원합니다.

```http
Authorization: Bearer eyJhbGci...
```

```java
if (auth != null
        && auth.startsWith("Bearer ")) {
    token = auth.substring(7);
}
```

Bearer  뒤의 JWT만 꺼냅니다.

---

### 2. Access Token 쿠키 확인

Authorization 헤더가 없으면 쿠키에서 찾습니다.

```java
else if (request.getCookies() != null) {
    String cookieName =
            authProperties.accessCookieNameOrDefault();

    for (var cookie : request.getCookies()) {
        if (cookieName.equals(cookie.getName())) {
            token = cookie.getValue();
            break;
        }
    }
}
```

기본 쿠키 이름은 다음과 같습니다.

```text
access_token
```

OnFilm은 두 방식을 모두 지원합니다.

```http
Authorization: Bearer <token>
또는
Cookie: access_token=<token>
```

둘 다 존재하면 Authorization 헤더를 우선 사용합니다.

---

### Access Token 검증

```java
if (token != null
        && !token.isBlank()
        && jwtProvider.validate(token)) {
    ...
}
```

validate()는 다음 내용을 확인합니다.

```java
public boolean validate(String token) {
    try {
        Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token);

        return true;
    } catch (
        JwtException |
        IllegalArgumentException exception
    ) {
        return false;
    }
}
```

주요 검증 내용은 다음과 같습니다.

JWT 형식이 올바른가?
서명이 서버 비밀키와 일치하는가?
토큰이 만료되지 않았는가?

검증에 실패하면 false를 반환합니다.

---

### 서명으로 변조를 찾는 방법

정상 토큰의 사용자 ID가 10이라고 가정하겠습니다.

```json
{
  "sub": "10"
}
```

공격자가 Payload를 다음처럼 바꿉니다.

```json
{
  "sub": "1"
}
```

하지만 공격자는 서버의 비밀키를 모르기 때문에 변경된 Payload에 맞는 새로운 서명을 만들 수 없습니다.

```text
변경된 Payload + 기존 Signature
→ 서명이 맞지 않음
→ 검증 실패
```

---

### Authentication 객체 생성

토큰이 유효하면 사용자 ID를 꺼냅니다.

```java
Long userId = parseUserId(token);

Claims claims = Jwts.parser()
        .verifyWith((SecretKey) key)
        .build()
        .parseSignedClaims(token)
        .getPayload();

return Long.valueOf(claims.getSubject());
```

sub에 넣어 둔 사용자 ID를 가져오는 것입니다.

```java
JWT subject = "10"
→ Long userId = 10L
```

그다음 Spring Security가 이해하는 인증 객체를 만듭니다.

```java
Authentication authentication =
        jwtProvider.getAuthentication(token);
```

현재 OnFilm에서는 사용자의 이름 자리에 userId 문자열을 사용합니다.

```java
User principal = new User(
    String.valueOf(userId),
    "",
    List.of()
);
```

---

### SecurityContext에 저장

```java
SecurityContextHolder
        .getContext()
        .setAuthentication(authentication);
```

SecurityContext는 현재 HTTP 요청의 로그인 정보를 보관합니다.

```text
현재 요청
└─ Authentication
   └─ principal
      └─ username = "10"
```

이제 Controller와 Service에서는 현재 사용자의 ID를 확인할 수 있습니다.

```java
Authentication authentication
```

또는 프로젝트의 SecurityUtil을 이용할 수 있습니다.

---

### 다음 Filter와 Controller로 전달

```java
filterChain.doFilter(
    request,
    response
);
```

JwtAuthFilter는 직접 API 응답을 만드는 것이 아니라 인증 정보를 등록한 뒤 요청을 다음 단계로 넘깁니다.

```text
JwtAuthFilter
→ 다른 보안 Filter
→ 권한 검사
→ Controller
```

---

### 토큰이 없거나 잘못되면?

JwtAuthFilter는 인증 정보를 등록하지 않고 요청을 다음 단계로 넘깁니다.

```text
토큰 없음 또는 검증 실패
→ SecurityContext에 Authentication 없음
```

#### 공개 API

```http
GET /api/people/{publicId}
```

로그인하지 않아도 허용된 주소라면 그대로 실행됩니다.

#### 인증이 필요한 API

```http
PUT /api/people/{publicId}
```

인증이 필요한 주소라면 Spring Security가 차단합니다.

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "인증이 필요합니다.",
  "errors": []
}
```

HTTP 상태는 401 Unauthorized입니다.

---

### JWT를 사용하면 매번 DB에서 세션을 찾지 않아도 됨

Access Token 자체에 사용자 ID와 만료 시간, 서명이 포함되어 있습니다.

서버는 각 요청에서 다음만 확인할 수 있습니다.

```text
토큰 서명
토큰 만료 시간
사용자 ID
```

따라서 서버 메모리에 로그인 세션을 보관하지 않는 방식으로 인증할 수 있습니다.

현재 OnFilm의 Spring Security 설정도 다음과 같습니다.

```java
.sessionCreationPolicy(
    SessionCreationPolicy.STATELESS
)
```

STATELESS는 서버가 HTTP 세션에 로그인 상태를 저장하지 않겠다는 의미입니다.

다만 프로필 수정처럼 실제 데이터를 처리할 때는 토큰에서 얻은 userId로 User를 DB에서 조회할 수 있습니다.

---

### 전체 인증 흐름

```text
로그인 성공
→ userId가 담긴 Access Token 생성
→ access_token 쿠키로 브라우저에 전달

이후 API 요청
→ 브라우저가 Access Token 쿠키 전송
→ JwtAuthFilter가 토큰 추출
→ 서명·만료 검증
→ subject에서 userId 추출
→ Authentication 생성
→ SecurityContext에 저장
→ Controller와 Service가 로그인 사용자로 인식
```

### 한 줄 요약

> OnFilm은 userId와 만료 시간을 담은 JWT Access Token에 서버 비밀키로 서명하고, 요청마다 JwtAuthFilter가 토큰을 검증해 로그인 사용자 정보를 SecurityContext에 등록합니다.

<!-- 학습 원문 끝: lesson-08 -->

</details>

---

<a id="lesson-09"></a>

## 1.2-2 Access Token과 Refresh Token을 나눈 이유

Access Token은 일반 API 인증에, Refresh Token은 재발급에 사용합니다. **짧게 사용하는 인증 수단과 오래 유지할 로그인 권한의 관리를 나눈 구조**입니다.

```yaml
access-token-ttl: 15m
refresh-token-ttl: 14d
```

Access는 DB에 저장하지 않는 JWT입니다. Refresh는 64바이트 난수로 만든 문자열이며 원문은 브라우저에, SHA-256 해시와 상태는 DB에 보관해 폐기·재사용을 관리합니다.

<details>
<summary>상세히 보기: 두 토큰의 수명·저장 위치·SHA-256 해싱과 발급 과정</summary>

<!-- 학습 원문 시작: lesson-09 -->

OnFilm은 로그인 성공 시 두 종류의 토큰을 발급합니다.

```text
Access Token  → API를 이용할 때 사용
Refresh Token → Access Token을 다시 발급받을 때 사용
```

현재 기본 유효시간은 다음과 같습니다.

```yaml
access-token-ttl: 15m
refresh-token-ttl: 14d
```

```text
Access Token  → 15분
Refresh Token → 14일
```

---

### Access Token의 역할

사용자가 인증이 필요한 API를 호출할 때 사용합니다.

```http
PUT /api/people/{publicId}
Cookie: access_token=<JWT>
```

Access Token에는 다음 정보가 들어 있습니다.

```text
사용자 ID
발급 시각
만료 시각
서명
```

서버는 JWT의 서명과 만료 시간을 검사해 사용자를 인증합니다.

```text
API 요청
→ Access Token 검증
→ userId 확인
→ 로그인 사용자로 처리
```

OnFilm은 Access Token 자체를 DB에 저장하지 않습니다.

---

### Refresh Token의 역할

Access Token이 15분 후 만료되면 사용자가 매번 이메일과 비밀번호로 다시 로그인해야 할 수 있습니다.

Refresh Token이 있으면 로그인 정보를 다시 입력하지 않고 새로운 Access Token을 받을 수 있습니다.

```text
Access Token 만료
→ Refresh Token으로 /auth/refresh 요청
→ 서버가 Refresh Token 확인
→ 새로운 Access Token 발급
```

Refresh Token으로 일반 API를 직접 이용하는 것은 아닙니다.

```text
프로필 수정에 Refresh Token 사용 → 안 됨
토큰 재발급에 Refresh Token 사용 → 가능
```

---

### 왜 토큰 하나만 오래 사용하지 않을까?

Access Token을 14일 동안 사용한다고 가정하겠습니다.

```text
Access Token 탈취
→ 공격자가 최대 14일 동안 API 사용 가능
```

JWT Access Token은 서버가 DB에서 매번 조회하지 않고 서명만 확인하는 방식입니다. 따라서 탈취 사실을 알아도 개별 토큰을 즉시 폐기하기 어렵습니다.

그래서 Access Token의 수명을 짧게 둡니다.

```text
Access Token 탈취
→ 사용할 수 있는 시간이 최대 15분 정도로 제한
```

대신 오래 로그인 상태를 유지하는 책임은 Refresh Token이 맡습니다.

Refresh Token은 DB에 발급 기록을 저장하므로 서버가 다음 작업을 할 수 있습니다.

```text
토큰 폐기
사용 여부 기록
만료 검사
재사용 탐지
사용자의 전체 로그인 세션 제거
```

정리하면 다음과 같습니다.

```text
Access Token
→ 자주 사용
→ 짧은 수명
→ 빠른 JWT 검증
→ DB 저장 안 함

Refresh Token
→ 재발급할 때만 사용
→ 긴 수명
→ DB에서 상태 관리
→ 폐기와 재사용 탐지 가능
```

---

### Refresh Token 발급 과정

로그인 성공 후 다음 코드가 실행됩니다.

```java
String refreshToken =
        refreshTokenService.issue(
            user.userId(),
            authProperties.refreshTokenTtl()
        );
```

실제 흐름은 다음과 같습니다.

```java
@Transactional
public String issue(Long userId, Duration ttl) {
    Long requiredUserId =
            requirePositiveUserId(userId);

    Duration requiredTtl =
            requireTtl(ttl);

    Instant now = clock.instant();

    String rawToken =
            jwtProvider.createRefreshToken();

    RefreshToken refreshToken =
            RefreshToken.issue(
                requiredUserId,
                tokenHashing.sha256(rawToken),
                now,
                now.plus(requiredTtl)
            );

    refreshTokenRepository.save(refreshToken);

    return rawToken;
}
```

---

### 1. Refresh Token 원문 생성

```java
public String createRefreshToken() {
    byte[] random = new byte[64];

    SecureRandomHolder.INSTANCE
            .nextBytes(random);

    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(random);
}
```

64바이트의 안전한 무작위 값을 생성하고 URL에서 사용할 수 있는 문자열로 바꿉니다.

Refresh Token은 JWT가 아닙니다.

```text
Access Token  → 사용자 정보가 들어 있는 JWT
Refresh Token → 의미 없는 강한 무작위 문자열
```

Refresh Token을 해석해서 사용자 ID를 얻는 것이 아니라 DB에서 해당 토큰의 발급 기록을 찾습니다.

---

### 2. 원문 대신 해시 저장

생성한 Refresh Token 원문은 그대로 DB에 저장하지 않습니다.

```text
tokenHashing.sha256(rawToken)

Refresh Token 원문
→ SHA-256 해싱
→ 해시값만 DB 저장
```

예를 들면 다음과 같습니다.

```text
브라우저가 받는 값
→ A7dK...긴 무작위 토큰...

DB에 저장하는 값
→ zP4m...SHA-256 해시...
```

DB가 노출되더라도 저장된 해시값을 그대로 Refresh Token으로 사용할 수 없도록 하기 위한 것입니다.

---

### 3. DB에 Refresh Token 정보 저장

```java
RefreshToken.issue(
    userId,
    tokenHash,
    issuedAt,
    expiresAt
);
```

DB에는 다음 정보가 저장됩니다.

```text
refresh_tokens
├─ id
├─ version
├─ user_id
├─ token_hash
├─ created_at
├─ expires_at
├─ revoked_at
└─ last_used_at
```

각 필드의 의미는 다음과 같습니다.

```text
user_id      → 누구의 토큰인가
token_hash   → 원문 토큰의 SHA-256 해시
created_at   → 언제 발급됐는가
expires_at   → 언제 만료되는가
revoked_at   → 언제 폐기됐는가
last_used_at → 마지막으로 언제 사용됐는가
version      → 동시 요청 충돌 확인
```

---

### 4. 브라우저에는 원문 반환

```java
return rawToken;
```

DB에는 해시값을 저장하지만 브라우저에는 원문을 전달합니다.

```text
브라우저
→ Refresh Token 원문 보관

DB
→ Refresh Token 해시 보관
```

재발급 요청이 들어오면 서버는 브라우저가 보낸 원문을 다시 SHA-256으로 해싱하고 DB의 해시값을 찾습니다.

```text
브라우저가 원문 전송
→ 서버가 SHA-256 해싱
→ DB의 token_hash와 비교
```

---

### 비밀번호와 Refresh Token의 해싱 차이

비밀번호는 사람이 직접 만들기 때문에 짧거나 추측하기 쉬울 수 있습니다.

```text
password123!
qwer1234
```

그래서 계산이 느린 BCrypt를 사용합니다.

Refresh Token은 서버가 64바이트의 강한 무작위 값으로 생성합니다.

예측하기 어려운 긴 무작위 값

그래서 OnFilm은 빠른 SHA-256으로 원문 노출만 방지합니다.

```text
비밀번호        → BCrypt
Refresh Token → SHA-256
```

---

### 전체 역할 비교

| 구분 | Access Token | Refresh Token |
| --- | --- | --- |
| 형태 | JWT | 무작위 문자열 |
| 용도 | 일반 API 인증 | Access Token 재발급 |
| 기본 수명 | 15분 | 14일 |
| DB 저장 | 저장하지 않음 | 해시로 저장 |
| 요청 빈도 | 대부분의 인증 API | 재발급 시에만 |
| 서버 제어 | 개별 폐기가 어려움 | 폐기·재사용 탐지 가능 |
| 브라우저 저장 | HttpOnly 쿠키 | HttpOnly 쿠키 |

### 한 줄 요약

> OnFilm은 일반 API 인증에는 수명이 짧은 JWT Access Token을 사용하고, 로그인 유지를 위해 수명이 긴 Refresh Token을 DB에서 별도로 관리해 보안과 사용 편의성을 함께 확보합니다.

<!-- 학습 원문 끝: lesson-09 -->

</details>

---

<a id="lesson-10"></a>

## 1.3 Refresh Token Rotation

Refresh Token으로 재발급할 때마다 **기존 토큰을 폐기하고 새 토큰으로 교체**합니다. 재사용된 옛 토큰을 알아볼 수 있게 하는 방식입니다.

```java
existing.consume(now);
refreshTokenRepository.saveAndFlush(existing);
// 새 Refresh Token을 생성한 뒤
refreshTokenRepository.save(newToken);
```

기존 토큰 폐기와 새 토큰 저장은 같은 DB 트랜잭션입니다. 다만 DB 커밋과 브라우저의 응답 수신까지 한 트랜잭션으로 묶이지는 않습니다. 새 쿠키를 받지 못한 재시도는 재사용으로 판단될 수 있습니다.

<details>
<summary>상세히 보기: required=false·원문 해싱·기존 토큰 폐기·새 토큰 저장</summary>

<!-- 학습 원문 시작: lesson-10 -->

Access Token이 만료되면 브라우저는 Refresh Token으로 재발급을 요청합니다.

```http
POST /auth/refresh
Cookie: refresh_token=<Refresh Token 원문>
```

OnFilm은 기존 Refresh Token을 계속 재사용하지 않습니다.

재발급할 때마다 다음과 같이 교체합니다.

```text
기존 Refresh Token 폐기
  → 새로운 Refresh Token 발급
```

이 방식을 Refresh Token Rotation이라고 합니다.

---

### 왜 Rotation을 사용할까?

같은 Refresh Token을 14일 동안 계속 사용한다고 가정하겠습니다.

```text
Refresh Token A 발급
→ 14일 동안 계속 사용
```

누군가 Token A를 탈취하면 원래 사용자와 공격자가 같은 토큰을 함께 사용할 수 있습니다.

Rotation을 적용하면:

```text
Refresh Token A로 재발급
→ A 폐기
→ Refresh Token B 발급
```

이후 A가 다시 들어오면 이미 폐기된 토큰이므로 이상 상황을 발견할 수 있습니다.

```text
정상 사용자 또는 공격자가 A 재사용
→ 폐기된 토큰 사용 감지
→ 탈취 가능성 의심
```

---

### 전체 재발급 흐름

```text
브라우저가 Refresh Token A 전송
        ↓
A를 SHA-256으로 해싱
        ↓
DB에서 A의 해시 조회
        ↓
폐기·만료 여부 확인
        ↓
A를 사용 완료 상태로 변경
        ↓
새로운 Refresh Token B 생성
        ↓
B의 해시를 DB에 저장
        ↓
새 Access Token 생성
        ↓
Access Token과 Refresh Token B를 쿠키로 응답
```

---

### 1. Controller가 Refresh Token을 받음

```java
@PostMapping("/refresh")
public ResponseEntity<AuthResponse> refresh(
        @CookieValue(
            name = "refresh_token",
            required = false
        )
        String refreshToken
) {
    if (refreshToken == null
            || refreshToken.isBlank()) {
        throw new InvalidRefreshTokenException();
    }

    AuthTokens tokens =
            authService.refresh(refreshToken);

    ...
}
```

Refresh Token은 요청 본문이 아니라 쿠키에서 꺼냅니다.

```java
@CookieValue(name = "refresh_token")
```

쿠키가 없거나 비어 있으면 재발급할 수 없습니다.

```text
InvalidRefreshTokenException
→ HTTP 401 Unauthorized
```

---

### 2. AuthService가 토큰 교체 요청

```java
@Transactional
public AuthTokens refresh(
        String rawRefreshToken
) {
    RefreshTokenService.RotationResult rotation =
            refreshTokenService.rotate(
                rawRefreshToken,
                authProperties.refreshTokenTtl()
            );

    String accessToken =
            jwtProvider.createAccessToken(
                rotation.userId(),
                authProperties.accessTokenTtl()
            );

    return new AuthTokens(
        accessToken,
        rotation.refreshToken()
    );
}
```

rotate()는 Refresh Token을 교체하고 다음 두 값을 반환합니다.

```java
public record RotationResult(
    Long userId,
    String refreshToken
) {
}
```

```text
userId       → 새 Access Token 생성에 사용
refreshToken → 새로 발급된 Refresh Token 원문
```

---

### RefreshTokenService.rotate()

```java
@Transactional
public RotationResult rotate(
        String rawToken,
        Duration ttl
) {
    ...
}
```

### 3. 전달받은 원문 검사

```java
String requiredRawToken =
        requireRawToken(rawToken);

Duration requiredTtl =
        requireTtl(ttl);
```

다음 값을 확인합니다.

Refresh Token이 null 또는 공백이 아닌가?
유효시간이 0보다 큰가?
최대 90일을 넘지 않는가?

현재 설정은 14일이므로 허용 범위입니다.

---

### 4. 원문을 해싱

```java
String hash =
        tokenHashing.sha256(requiredRawToken);
```

DB에는 원문이 아니라 해시가 저장되어 있으므로, 브라우저가 보낸 원문도 같은 방식으로 해싱합니다.

```text
브라우저가 보낸 원문 A
→ SHA-256
→ 해시 X
```

---

### 5. DB에서 토큰 조회

```java
RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                InvalidRefreshTokenException::new
            );
```

DB에서 같은 해시값을 가진 토큰을 찾습니다.

```text
찾음    → 다음 검사 진행
못 찾음 → 유효하지 않은 토큰, HTTP 401
```

---

### 6. 폐기와 만료 상태 검사

```java
if (existing.isRevoked()) {
    // 재사용 감지
}

if (existing.isExpiredAt(now)) {
    // 만료 토큰 처리
}
```

정상적인 Rotation은 다음 조건을 모두 만족해야 합니다.

```text
DB에 존재함
아직 폐기되지 않음
아직 만료되지 않음
```

폐기된 토큰 재사용과 만료 토큰 처리는 다음 단계에서 자세히 살펴봅니다.

---

### 7. 기존 Token A를 사용 완료 처리

```java
existing.consume(now);
```

consume()은 다음 값을 설정합니다.

```java
this.lastUsedAt = usedAt;
this.revokedAt = usedAt;
```

즉, Token A가 사용된 시각을 기록하면서 다시 사용할 수 없도록 폐기합니다.

```java
Token A
last_used_at = 현재 시각
revoked_at   = 현재 시각
```

이후 DB에 즉시 반영합니다.

```java
refreshTokenRepository.saveAndFlush(existing);
```

---

### 8. 새로운 Token B 생성

```java
String newRawToken =
        jwtProvider.createRefreshToken();
```

새로운 64바이트 무작위 토큰을 생성합니다.

기존 Token A와 전혀 다른 Token B

---

### 9. Token B의 해시 저장

```java
RefreshToken newToken =
        RefreshToken.issue(
            existing.getUserId(),
            tokenHashing.sha256(newRawToken),
            now,
            now.plus(requiredTtl)
        );

refreshTokenRepository.save(newToken);
```

DB에는 새로운 토큰의 해시만 저장합니다.

```text
Token A 기록
revoked_at = 현재 시각

Token B 기록
token_hash = B의 SHA-256 해시
expires_at = 현재 + 14일
revoked_at = null
```

브라우저에는 B의 원문을 반환합니다.

```java
return new RotationResult(
    existing.getUserId(),
    newRawToken
);
```

---

### DB 상태 변화

### Rotation 전

```java
Token A
user_id    = 10
revoked_at = null
expires_at = 14일 후
```

### Rotation 후

```java
Token A
user_id      = 10
last_used_at = 현재 시각
revoked_at   = 현재 시각

Token B
user_id      = 10
created_at   = 현재 시각
expires_at   = 14일 후
revoked_at   = null
```

사용자는 앞으로 Token B만 사용할 수 있습니다.

---

### 왜 하나의 트랜잭션으로 처리할까?

기존 토큰 폐기와 새 토큰 발급은 하나의 작업입니다.

```text
Token A 폐기
Token B 발급
```

둘 중 하나만 성공하면 문제가 생깁니다.

#### A만 폐기되고 B 생성 실패

사용 가능한 Refresh Token이 없어짐

#### B는 생성됐는데 A 폐기 실패

A와 B를 모두 사용할 수 있음

그래서 같은 트랜잭션으로 묶습니다.

```text
A 폐기 성공
→ B 저장 성공
→ Commit

중간에 실패
→ A 폐기와 B 저장 모두 Rollback
```

---

### 새 쿠키 전달

Rotation이 성공하면 Controller가 세가지 쿠키를 다시 만듭니다.

```text
새 Access Token
새 Refresh Token B
새 CSRF Token
```

응답 헤더로 브라우저에 전달합니다.

```http
Set-Cookie: access_token=<새 JWT>
Set-Cookie: refresh_token=<Token B>
Set-Cookie: XSRF-TOKEN=<새 CSRF Token>
```

브라우저의 기존 쿠키는 같은 이름의 새 쿠키로 교체됩니다.

---

### 한 줄 요약

> Refresh Token Rotation은 재발급 요청마다 기존 Refresh Token을 폐기하고 새 토큰으로 교체하여, 하나의 토큰이 반복 사용되는 것을 막고 탈취된 토큰의 재사용을 발견할 수 있게 하는 방식입니다.

<!-- 학습 원문 끝: lesson-10 -->

</details>

---

<a id="lesson-11"></a>

## 1.3-1 폐기된 Refresh Token 재사용 탐지

이미 폐기된 Refresh Token이 들어오면 탈취 가능성으로 보고 **해당 사용자의 모든 Refresh Token을 삭제**합니다. 재발급 실패의 롤백과 별개로 삭제를 확정해야 합니다.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void deleteAllSessionsForReuse(Long userId) {
    refreshTokenRepository.deleteAllByUserId(userId);
    refreshTokenRepository.flush();
}
```

새 트랜잭션에서 삭제를 커밋한 뒤 바깥 재발급은 실패시킵니다. 정상적인 중복 요청도 이 정책에 걸릴 수 있습니다. 기존 Access JWT는 즉시 폐기되지 않고 남은 유효기간 동안 사용할 수 있습니다.

<details>
<summary>상세히 보기: 왜 모든 Refresh Token을 삭제하며 REQUIRES_NEW가 필요할까?</summary>

<!-- 학습 원문 시작: lesson-11 -->

정상적인 Rotation은 다음과 같습니다.

```text
Refresh Token A 사용
→ A 폐기
→ Refresh Token B 발급
```

이후에는 B만 사용해야 합니다.

그런데 폐기된 A가 다시 들어오면 OnFilm은 단순한 로그인 실패가 아니라 토큰 탈취 가능성으로 판단합니다.

---

### 재사용은 어떻게 발생할까?

다음 상황을 가정하겠습니다.

```text
사용자가 Refresh Token A를 가지고 있음
공격자가 Token A를 복사함
```

정상 사용자가 먼저 재발급합니다.

```text
정상 사용자
→ Token A 사용
→ A 폐기
→ Token B 발급
```

그 후 공격자가 복사해 둔 A를 사용합니다.

```text
공격자
→ 폐기된 Token A 사용
→ 재사용 탐지
```

서버는 누가 정상 사용자이고 누가 공격자인지 정확하게 구분할 수 없습니다.

따라서 안전한 방향을 선택합니다.

```text
해당 사용자의 모든 Refresh Token 제거
→ 모든 기기에서 다시 로그인하도록 함
```

---

### 코드 흐름

```java
RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                InvalidRefreshTokenException::new
            );

if (existing.isRevoked()) {
    Long userId = existing.getUserId();

    log.warn(
        "Refresh token reuse detected; "
            + "deleting all sessions for userId={}",
        userId
    );

    securityTransactionService
            .deleteAllSessionsForReuse(userId);

    throw new RefreshTokenReuseDetectedException();
}
```

---

### 1. 토큰의 해시로 기록 조회

브라우저가 보낸 Token A를 해싱합니다.

```text
Token A 원문
→ SHA-256
→ 해시 X
```

DB에서 해시 X를 찾습니다.

```java
findByTokenHash(hash);
```

기록이 있다는 것은 과거에 서버가 발급한 토큰이라는 의미입니다.

---

### 2. 폐기 여부 확인

```text
existing.isRevoked()
```

내부에서는 다음 값을 확인합니다.

```java
public boolean isRevoked() {
    return revokedAt != null;
}
```

정상적으로 Rotation에 사용된 토큰은 revokedAt이 기록되어 있습니다.

```java
Token A
revoked_at = 2026-09-16 10:00:00
```

따라서 A가 다시 들어오면 재사용으로 판단합니다.

---

### 3. 모든 Refresh Token 제거

```java
deleteAllSessionsForReuse(userId);
```

내부 코드는 다음과 같습니다.

```java
@Transactional(
    propagation = Propagation.REQUIRES_NEW
)
public void deleteAllSessionsForReuse(Long userId) {
    refreshTokenRepository.deleteAllByUserId(userId);
    refreshTokenRepository.flush();
}
```

같은 사용자의 모든 Refresh Token 기록을 삭제합니다.

```text
Token A → 삭제
Token B → 삭제
다른 기기의 Token C → 삭제
```

따라서 해당 사용자가 가진 모든 Refresh Token이 무효가 됩니다.

Access Token은 DB에 저장하지 않으므로 이미 발급된 Access Token은 남은 유효시간 동안 사용할 수 있습니다. 하지만 기본 수명이
15분으로 짧고, 만료된 후에는 Refresh Token이 없으므로 다시 로그인해야 합니다.

---

### REQUIRES_NEW는 무엇인가?

기존 rotate()에도 트랜잭션이 있습니다.

```java
@Transactional
public RotationResult rotate(...) {
}
```

재사용을 발견한 뒤 다음 예외를 발생시킵니다.

```java
throw new RefreshTokenReuseDetectedException();
```

이 예외는 RuntimeException이므로 기존 트랜잭션은 Rollback됩니다.

만약 모든 세션 삭제도 기존 트랜잭션 안에서 실행된다면:

```text
모든 Refresh Token 삭제
→ 재사용 예외 발생
→ 기존 트랜잭션 Rollback
→ 삭제도 취소
```

보안을 위해 삭제했는데 Rollback으로 다시 살아나는 문제가 생깁니다.

그래서 별도의 새 트랜잭션을 사용합니다.

```java
@Transactional(
    propagation = Propagation.REQUIRES_NEW
)
```

REQUIRES_NEW의 의미는 다음과 같습니다.

> 진행 중인 트랜잭션과 별개로 새로운 트랜잭션을 시작한다.

---

### 실제 실행 순서

```text
rotate()의 기존 트랜잭션 시작
        ↓
폐기된 Token A 발견
        ↓
기존 트랜잭션 잠시 중단
        ↓
REQUIRES_NEW 트랜잭션 시작
        ↓
사용자의 모든 Refresh Token 삭제
        ↓
새 트랜잭션 Commit
        ↓
기존 rotate() 트랜잭션으로 돌아감
        ↓
RefreshTokenReuseDetectedException 발생
        ↓
기존 트랜잭션 Rollback
```

기존 트랜잭션이 Rollback돼도 새 트랜잭션에서 이미 Commit한 삭제 결과는 유지됩니다.

```text
세션 삭제 결과   → 유지
재발급 작업     → 실패
```

---

### 사용자 응답

RefreshTokenReuseDetectedException은 외부에서 다음 오류로 처리됩니다.

```text
ErrorCode.INVALID_REFRESH_TOKEN
HTTP 401 Unauthorized
```

```json
{
  "code": "INVALID_REFRESH_TOKEN",
  "message": "유효하지 않은 리프레시 토큰입니다.",
  "errors": []
}
```

“토큰 탈취가 탐지됐다”는 구체적인 내부 판단을 응답에 자세히 노출하지 않고, 공통적인 유효하지 않은 토큰 오류를 반환합니다.

서버 로그에는 다음처럼 보안 사건을 남깁니다.

```java
Refresh token reuse detected;
deleting all sessions for userId=10
```

토큰 원문이나 토큰 해시는 로그에 남기지 않습니다.

---

### 이 방식의 비용

폐기된 토큰이 다시 들어오면 공격이 아니라 네트워크 문제로 인한 중복 요청일 수도 있습니다.

그런데도 OnFilm은 안전을 우선해 모든 세션을 삭제합니다.

```text
장점
→ 탈취 가능성이 있을 때 모든 장기 세션 차단

비용
→ 정상적인 중복 요청이어도 사용자가 다시 로그인해야 할 수 있음
→ 다른 기기의 로그인도 함께 해제됨
```

이것은 보안을 우선한 정책 선택입니다.

### 한 줄 요약

> OnFilm은 이미 폐기된 Refresh Token이 다시 사용되면 탈취 가능성으로 판단하고, REQUIRES_NEW 트랜잭션으로 해당 사용자의 모든 Refresh Token을 삭제한 뒤 재로그인을 요구합니다.

<!-- 학습 원문 끝: lesson-11 -->

</details>

---

<a id="lesson-12"></a>

## 1.3-2 Refresh Token 동시 요청과 낙관적 락

같은 Refresh Token을 동시에 사용하면 **@Version으로 다른 요청이 먼저 변경했는지 확인**합니다. 충돌한 요청은 새 토큰을 발급하지 않고 401로 처리합니다.

```sql
-- 개념적인 UPDATE 조건
UPDATE refresh_tokens
SET revoked_at = ?, version = 1
WHERE id = 100 AND version = 0;
```

두 요청이 같은 version을 읽은 경우의 설명입니다. 늦은 요청이 폐기 완료 후 조회하면 낙관적 락 대신 재사용 탐지로 전체 Refresh Token을 삭제할 수 있습니다.

<details>
<summary>상세히 보기: version 0을 읽은 두 요청 중 하나만 UPDATE에 성공하는 과정</summary>

<!-- 학습 원문 시작: lesson-12 -->

같은 Refresh Token으로 재발급 요청이 거의 동시에 두 번 들어올 수 있습니다.

```text
요청 A → Refresh Token X
요청 B → Refresh Token X
```

발생 원인은 다양합니다.

```text
사용자가 버튼을 연속으로 누름
프론트엔드가 요청을 중복 전송
네트워크 재시도
여러 탭에서 동시에 재발급
공격자가 같은 토큰을 사용
```

락이 없다면 두 요청이 모두 성공할 수 있습니다.

```text
요청 A → Token X 확인 → 새 Token A 발급
요청 B → Token X 확인 → 새 Token B 발급
```

한 번만 사용해야 하는 Refresh Token으로 새 토큰이 두 개 만들어지는 문제가 발생합니다.

---

### @Version을 이용한 낙관적 락

RefreshToken에는 다음 필드가 있습니다.

```java
@Version
@Column(nullable = false)
private Long version;
```

JPA는 @Version 필드를 이용해 같은 데이터를 다른 트랜잭션이 먼저 변경했는지 확인합니다.

처음 저장된 토큰은 다음과 같다고 가정하겠습니다.

```text
id         = 100
version    = 0
revoked_at = null
```

---

### 두 요청이 동시에 조회

```text
요청 A가 Token X 조회
→ version = 0

요청 B가 Token X 조회
→ version = 0
```

두 요청 모두 자신이 조회한 값이 최신이라고 생각합니다.

---

### 요청 A가 먼저 변경

```java
existing.consume(now);
refreshTokenRepository.saveAndFlush(existing);
```

JPA는 개념적으로 다음과 같은 SQL을 실행합니다.

```sql
UPDATE refresh_tokens
SET revoked_at = ?,
    last_used_at = ?,
    version = 1
WHERE id = 100
  AND version = 0;
```

현재 DB의 version이 0이므로 수정에 성공합니다.

```text
id         = 100
version    = 1
revoked_at = 현재 시각
```

---

### 요청 B도 변경 시도

요청 B는 앞에서 version = 0으로 읽었습니다.

따라서 같은 조건으로 UPDATE를 시도합니다.

```sql
UPDATE refresh_tokens
SET revoked_at = ?,
    last_used_at = ?,
    version = 1
WHERE id = 100
  AND version = 0;
```

하지만 요청 A가 이미 DB의 version을 1로 바꿨습니다.

```text
DB 현재 version = 1
요청 B 조건       = version 0
```

조건에 맞는 행이 없으므로 수정된 행 수가 0이 됩니다.

JPA는 이를 다음과 같이 판단합니다.

> 내가 조회한 후 다른 트랜잭션이 먼저 데이터를 변경했다.

그리고 낙관적 락 예외를 발생시킵니다.

---

### OnFilm의 예외 처리

```java
try {
    existing.consume(now);
    refreshTokenRepository.saveAndFlush(existing);
} catch (
    OptimisticLockingFailureException exception
) {
    throw new InvalidRefreshTokenException(exception);
}
```

동시에 들어온 요청 중 하나만 성공합니다.

```text
요청 A
→ 기존 Token X 폐기 성공
→ 새 Refresh Token 발급
→ HTTP 200

요청 B
→ version 충돌
→ InvalidRefreshTokenException
→ Rollback
→ HTTP 401
```

두 번째 요청에서는 새로운 Refresh Token이 만들어지지 않습니다.

---

### 왜 saveAndFlush()가 필요할까?

save()만 호출하면 실제 UPDATE가 트랜잭션 Commit 시점까지 미뤄질 수 있습니다.

```text
save()
→ 아직 UPDATE 안 될 수 있음
→ try-catch 종료
→ 나중에 version 충돌 발생
```

그러면 현재 try-catch에서 낙관적 락 예외를 잡기 어려울 수 있습니다.

```java
saveAndFlush(existing);
```

를 사용하면 이 위치에서 UPDATE를 실행해 version 충돌을 확인할 수 있습니다.

---

### 낙관적 락이라는 이름의 의미

낙관적 락은 데이터를 조회할 때 DB 행을 잠그지 않습니다.

```text
“충돌이 자주 발생하지 않을 것”이라고 예상
→ 일단 각 요청이 데이터를 읽음
→ 수정할 때 version으로 충돌 확인
```

반대 방식은 비관적 락입니다.

데이터를 조회할 때부터 다른 요청이 수정하지 못하도록 잠금

Refresh Token 재발급은 같은 토큰으로 동시 요청이 자주 발생하는 정상 기능은 아닙니다. 따라서 조회할 때부터 DB를 잠그기보다
version으로 충돌을 발견하는 낙관적 락을 사용했습니다.

---

### version은 직접 바꾸지 않음

애플리케이션 코드에서는 다음처럼 수정하지 않습니다.

```java
version++;
```

JPA가 @Version 필드를 보고 UPDATE할 때 자동으로 증가시킵니다.

```text
정상 UPDATE
version 0 → 1

다음 정상 UPDATE
version 1 → 2
```

---

### Rotation 전체 흐름과 연결

```text
Refresh Token X로 요청 A와 B가 동시에 들어옴
        ↓
두 요청 모두 version 0 조회
        ↓
요청 A가 X를 폐기하고 version 1로 변경
        ↓
요청 B는 version 0 조건으로 변경 시도
        ↓
수정할 행이 없어서 낙관적 락 충돌
        ↓
요청 A만 새로운 Refresh Token 발급
요청 B는 HTTP 401
```

### 한 줄 요약

> OnFilm은 Refresh Token의 @Version을 이용해 같은 토큰으로 들어온 동시 재발급 요청을 감지하고, 먼저 처리한 요청 하나만 성공시키며 나머지는 유효하지 않은 토큰으로 거부합니다.

<!-- 학습 원문 끝: lesson-12 -->

</details>

---

<a id="lesson-13"></a>

## 1.3-3 만료된 Refresh Token 처리

현재 시각이 만료 시각과 같거나 이후면 재발급을 거부합니다. **만료 토큰의 사용 시도는 별도 트랜잭션으로 기록**합니다.

```java
if (existing.isExpiredAt(now)) {
    securityTransactionService.recordExpiredUse(existing.getId(), now);
    throw new InvalidRefreshTokenException();
}
```

recordExpiredUse()는 REQUIRES_NEW에서 lastUsedAt·revokedAt을 저장합니다. 바깥 재발급이 롤백돼도 기록이 남습니다. 이후 같은 폐기 토큰으로 재시도하면 재사용 검사에 걸릴 수 있습니다.

<details>
<summary>상세히 보기: 만료 시각 비교와 실패 후에도 남아야 하는 사용 기록</summary>

<!-- 학습 원문 시작: lesson-13 -->

Refresh Token에는 만료 시각이 저장됩니다.

```text
created_at = 2026-09-01
expires_at = 2026-09-15
```

현재 시각이 만료 시각과 같거나 이후라면 사용할 수 없습니다.

```java
public boolean isExpiredAt(Instant instant) {
    return !instant.isBefore(expiresAt);
}
```

쉽게 풀면 다음과 같습니다.

```text
현재 시각 < 만료 시각
→ 아직 유효함

현재 시각 >= 만료 시각
→ 만료됨
```

---

### 만료 검사 흐름

```java
if (existing.isExpiredAt(now)) {
    securityTransactionService
            .recordExpiredUse(
                existing.getId(),
                now
            );

    throw new InvalidRefreshTokenException();
}
```

만료된 토큰이 사용되면 다음 두 가지 일을 합니다.

1. 만료 토큰이 사용됐다는 사실 기록
2. 재발급은 거부

---

### 왜 만료된 토큰을 다시 기록할까?

토큰은 만료됐지만 DB의 revokedAt은 아직 null일 수 있습니다.

```text
expires_at = 이미 지남
revoked_at = null
```

만료된 토큰 사용 시 다음 값을 기록합니다.

```text
last_used_at = 사용을 시도한 시각
revoked_at   = 사용을 시도한 시각
```

결과는 다음과 같습니다.

이 토큰은 만료됐고,
만료된 후 실제 사용 시도도 있었다.

이 정보는 다음과 같은 목적으로 활용할 수 있습니다.

- 만료된 토큰 사용 흔적 확인
- 토큰 상태를 명확하게 폐기 상태로 변경
- 이후 토큰 정리 작업의 대상 판단

---

### recordExpiredUse()

```java
@Transactional(
    propagation = Propagation.REQUIRES_NEW
)
public void recordExpiredUse(
        Long tokenId,
        Instant usedAt
) {
    RefreshToken token =
            refreshTokenRepository
                .findById(tokenId)
                .orElseThrow(
                    InvalidRefreshTokenException::new
                );

    token.revoke(usedAt);

    refreshTokenRepository
            .saveAndFlush(token);
}
```

### 토큰 다시 조회

```text
findById(tokenId)
```

새로운 트랜잭션에서 해당 토큰을 다시 조회합니다.

### 폐기 처리

```java
token.revoke(usedAt);
```

내부적으로 다음 값을 설정합니다.

```text
lastUsedAt = usedAt
revokedAt  = usedAt
```

### 즉시 DB 반영

```java
saveAndFlush(token);
```

현재 새 트랜잭션 안에서 UPDATE를 실행합니다.

---

### 왜 REQUIRES_NEW를 사용할까?

기존 rotate() 트랜잭션은 마지막에 예외를 발생시킵니다.

```java
throw new InvalidRefreshTokenException();
```

만약 같은 트랜잭션에서 만료 사용 기록을 저장하면:

```text
만료 사용 기록
→ InvalidRefreshTokenException 발생
→ 기존 트랜잭션 Rollback
→ 기록도 취소
```

그래서 별도 트랜잭션으로 기록합니다.

```text
기존 rotate 트랜잭션 시작
        ↓
만료된 토큰 확인
        ↓
새 트랜잭션 시작(REQUIRES_NEW)
        ↓
last_used_at·revoked_at 기록
        ↓
새 트랜잭션 Commit
        ↓
기존 트랜잭션으로 복귀
        ↓
InvalidRefreshTokenException 발생
        ↓
기존 트랜잭션 Rollback
```

기존 재발급 작업은 실패하지만, 만료 토큰 사용 기록은 유지됩니다.

---

### 사용자 응답

사용자에게는 다음 응답을 반환합니다.

```json
{
  "code": "INVALID_REFRESH_TOKEN",
  "message": "유효하지 않은 리프레시 토큰입니다.",
  "errors": []
}
```

```java
HTTP 401 Unauthorized
```

서버가 다음 경우를 사용자에게 세세하게 구분하지는 않습니다.

```text
DB에 없는 토큰
만료된 토큰
동시 요청으로 충돌한 토큰
```

모두 재발급에 사용할 수 없는 토큰이므로 공통 오류를 반환합니다.

---

### 만료된 Access Token과 차이

### Access Token

```text
JWT의 exp 확인
→ 만료됐다면 인증하지 않음
→ DB 기록 없음
```

### Refresh Token

```text
DB의 expires_at 확인
→ 만료됐다면 재발급 거부
→ 사용 시도를 last_used_at과 revoked_at에 기록
```

Refresh Token은 장기 로그인 권한과 연결되어 있어 DB에서 상태를 더 자세히 관리합니다.

### 한 줄 요약

> OnFilm은 만료된 Refresh Token으로 재발급을 시도하면 요청을 거부하고, REQUIRES_NEW 트랜잭션으로 사용 시각과 폐기 상태를 기록해 외부 재발급 트랜잭션이 롤백돼도 보안 기록을 남깁니다.

<!-- 학습 원문 끝: lesson-13 -->

</details>

---

<a id="lesson-14"></a>

## 1.3-4 로그아웃 처리

로그아웃은 **현재 Refresh Token을 DB에서 폐기하고 브라우저의 인증 쿠키를 삭제**합니다. 이미 없거나 폐기된 토큰으로 다시 요청해도 정상 종료합니다.

```java
authService.logout(refreshToken);
ResponseCookie accessCookie = authCookieFactory.deleteAccessCookie();
ResponseCookie csrfCookie = authCookieFactory.deleteCsrfCookie();
ResponseCookie refreshCookie = authCookieFactory.deleteRefreshCookie();
```

삭제 쿠키는 같은 이름·Path 등에 Max-Age=0을 적용하며 성공 응답은 204입니다. 복사된 Access JWT는 만료 전까지 유효할 수 있습니다. 현재 GET 로그아웃도 허용하므로 POST 제한과 CSRF 정책은 보완 대상입니다.

<details>
<summary>상세히 보기: 반복 로그아웃·일괄 UPDATE·쿠키 삭제·Access Token의 한계</summary>

<!-- 학습 원문 시작: lesson-14 -->

로그아웃은 단순히 브라우저의 쿠키만 지우는 작업이 아닙니다.

OnFilm은 다음 두 작업을 함께 수행합니다.

1. DB의 Refresh Token 폐기
2. 브라우저의 인증 쿠키 삭제

---

### 로그아웃 요청

현재 OnFilm은 다음 두 HTTP 방식을 허용합니다.

```http
POST /auth/logout
GET /auth/logout
```

Controller 코드는 다음과 같습니다.

```java
@RequestMapping(
    value = "/logout",
    method = {
        RequestMethod.POST,
        RequestMethod.GET
    }
)
public ResponseEntity<Void> logout(
        @CookieValue(
            name = "refresh_token",
            required = false
        )
        String refreshToken
) {
    authService.logout(refreshToken);

    ResponseCookie accessCookie =
            authCookieFactory.deleteAccessCookie();

    ResponseCookie csrfCookie =
            authCookieFactory.deleteCsrfCookie();

    ResponseCookie refreshCookie =
            authCookieFactory.deleteRefreshCookie();

    return ResponseEntity.noContent()
            .header(
                HttpHeaders.SET_COOKIE,
                accessCookie.toString()
            )
            .header(
                HttpHeaders.SET_COOKIE,
                csrfCookie.toString()
            )
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie.toString()
            )
            .build();
}
```

---

### 1. Refresh Token을 쿠키에서 꺼냄

```java
@CookieValue(
    name = "refresh_token",
    required = false
)
String refreshToken
```

```text
required = false이므로 Refresh Token 쿠키가 없어도 로그아웃 요청은 실패하지 않습니다.

Refresh Token 있음
→ DB에서 폐기 처리

Refresh Token 없음
→ DB 작업 생략
→ 쿠키 삭제 응답은 그대로 수행
```

로그아웃은 여러 번 요청해도 같은 결과가 나오도록 만들었습니다.

```text
첫 번째 로그아웃 → 성공
두 번째 로그아웃 → 이미 로그아웃 상태지만 성공
```

이런 성질을 멱등성이라고 합니다.

---

### 2. AuthService가 폐기 요청

```java
@Transactional
public void logout(String rawRefreshToken) {
    refreshTokenService.revoke(
        rawRefreshToken
    );
}
```

revoke()는 전달받은 Refresh Token을 더 이상 사용할 수 없게 만듭니다.

---

### 3. 토큰이 없으면 종료

```java
public void revoke(String rawToken) {
    if (rawToken == null
            || rawToken.isBlank()) {
        return;
    }

    ...
}
```

쿠키가 없거나 비어 있으면 예외를 발생시키지 않고 종료합니다.

사용자 입장에서는 이미 로그아웃된 상태와 같기 때문입니다.

---

### 4. 원문을 해싱해 DB Token 조회

```text
tokenHashing.sha256(rawToken)
```

브라우저에는 Refresh Token 원문이 있지만 DB에는 해시만 저장되어 있습니다.

```text
Refresh Token 원문
→ SHA-256 해싱
→ DB token_hash와 비교
```

---

### 5. 활성 Token 폐기

```java
refreshTokenRepository
        .revokeActiveByTokenHash(
            tokenHashing.sha256(rawToken),
            clock.instant()
        );
```

Repository는 다음 JPQL을 실행합니다.

```java
@Query("""
    update RefreshToken rt
       set rt.lastUsedAt = :revokedAt,
           rt.revokedAt = :revokedAt,
           rt.version = rt.version + 1
     where rt.tokenHash = :tokenHash
       and rt.revokedAt is null
""")
```

개념적인 SQL은 다음과 같습니다.

```sql
UPDATE refresh_tokens
SET last_used_at = 현재시각,
    revoked_at = 현재시각,
    version = version + 1
WHERE token_hash = ?
  AND revoked_at IS NULL;
```

revoked_at IS NULL 조건 때문에 아직 활성 상태인 토큰만 폐기합니다.

---

### 이미 폐기된 토큰이면?

```text
revoked_at IS NULL 조건에 맞지 않음
→ 수정된 행 0개
→ 오류 없이 종료
```

### DB에 존재하지 않는 토큰이면?

```text
token_hash 조건에 맞는 행 없음
→ 수정된 행 0개
→ 오류 없이 종료
```

따라서 로그아웃 요청은 반복해도 안전합니다.

---

### 6. 브라우저 쿠키 삭제

Controller는 세 쿠키를 삭제하는 응답을 만듭니다.

```java
deleteAccessCookie();
deleteRefreshCookie();
deleteCsrfCookie();
```

쿠키를 삭제할 때는 빈 값을 넣고 maxAge(0)을 설정합니다.

```java
ResponseCookie.from(cookieName, "")
        .maxAge(0)
        .build();
```

응답은 개념적으로 다음과 같습니다.

```http
Set-Cookie: access_token=; Max-Age=0
Set-Cookie: refresh_token=; Max-Age=0
Set-Cookie: XSRF-TOKEN=; Max-Age=0
```

브라우저는 해당 쿠키를 제거합니다.

쿠키를 삭제할 때도 생성할 때와 동일한 이름과 Path를 사용해야 합니다.

```java
access_token  Path=/
refresh_token Path=/auth
XSRF-TOKEN    Path=/
```

Path가 다르면 기존 쿠키가 삭제되지 않을 수 있습니다.

---

### 7. 응답

```java
return ResponseEntity.noContent()
        ...
        .build();
```

HTTP 상태는 다음과 같습니다.

```text
204 No Content
```

로그아웃은 성공했지만 반환할 JSON 본문은 없다는 의미입니다.

---

### Access Token도 서버에서 즉시 폐기되나?

아닙니다. Access Token은 JWT이고 DB에 저장하지 않으므로, 현재 구조에서는 개별 토큰을 서버에서 즉시 폐기하지 않습니다.

로그아웃하면 브라우저의 Access Token 쿠키는 삭제됩니다.

```text
브라우저에 있던 Access Token
→ 쿠키 삭제
→ 이후 요청에서 전송되지 않음
```

하지만 누군가 Access Token을 미리 복사해 갔다면 만료될 때까지 사용할 가능성이 있습니다.

이 위험을 줄이기 위해 Access Token 수명을 15분으로 짧게 설정했습니다.

```text
Refresh Token
→ DB에서 즉시 폐기

Access Token
→ 브라우저 쿠키 삭제
→ 최대 15분 후 자체 만료
```

Access Token까지 즉시 무효화하려면 별도의 차단 목록이나 사용자 토큰 버전 같은 추가 구조가 필요합니다. 하지만 매 요청마다 상태 확인이 필요해 JWT의 단순성과 성능상 장점이 줄어드는 트레이드오프가 있습니다.

---

### GET 로그아웃에 대한 주의점

현재 코드는 편의를 위해 GET과 POST를 모두 허용합니다.

```java
method = {
    RequestMethod.POST,
    RequestMethod.GET
}
```

하지만 로그아웃은 서버의 Refresh Token 상태를 변경하는 작업입니다.

HTTP 원칙상 상태를 변경하는 기능은 일반적으로 POST를 사용하는 편이 더 적절합니다.

```text
GET  → 데이터 조회
POST → 상태 변경
```

특히 브라우저 환경에서는 GET 로그아웃이 링크 접근, 미리보기 또는 외부 요청만으로 실행될 가능성이 있습니다. 실제 운영 정책을 강화한다면 POST /auth/logout만 허용하고 CSRF 검증을 적용하는 방향을 고려할 수 있습니다.

---

### 전체 로그아웃 흐름

```http
POST /auth/logout
        ↓
refresh_token 쿠키 읽기
        ↓
원문을 SHA-256 해싱
        ↓
DB의 활성 Refresh Token 폐기
        ↓
Access Token 쿠키 삭제
        ↓
Refresh Token 쿠키 삭제
        ↓
CSRF Token 쿠키 삭제
        ↓
HTTP 204 No Content
```

### 한 줄 요약

> OnFilm 로그아웃은 Refresh Token을 DB에서 폐기하고 브라우저의 Access·Refresh·CSRF 쿠키를 삭제하며, 같은 요청을 반복해도 오류가 나지 않도록 멱등하게 처리합니다.

<!-- 학습 원문 끝: lesson-14 -->

</details>

---

<a id="lesson-15"></a>

## 1.4 쿠키 보안 옵션

쿠키 옵션은 **누가 값을 읽는지, 어떤 요청에 보내는지, 얼마나 보관하는지**를 정합니다. 인증 쿠키는 HttpOnly이고, CSRF 쿠키는 프론트엔드가 읽어야 하므로 HttpOnly가 아닙니다.

| 옵션 | 역할 |
|---|---|
| HttpOnly | JavaScript의 쿠키 값 읽기 제한 |
| Secure | HTTPS에서 전송하도록 제한 |
| SameSite | 교차 사이트 요청의 쿠키 전송 제한 |
| Path | 전송할 URL 경로 범위 |
| Max-Age | 브라우저 보관 시간 |

Secure=true는 HTTPS 운영 권장값이며 확인 당시 prod 파일의 값은 false입니다. CSRF 쿠키는 XSRF-TOKEN, 실제 헤더는 X-CSRF-TOKEN입니다. 원문의 다른 표기는 아래 상세 설명에 보존하고 검토 메모로 구분했습니다.

<details>
<summary>상세히 보기: HttpOnly·Secure·SameSite·Path·Max-Age 각각의 의미</summary>

<!-- 학습 원문 시작: lesson-15 -->

OnFilm은 로그인 성공 후 세 가지 쿠키를 발급합니다.

```text
access_token
refresh_token
XSRF-TOKEN
```

쿠키에는 값뿐 아니라 브라우저가 쿠키를 어떻게 다룰지 정하는 옵션이 있습니다.

```http
Set-Cookie: access_token=<JWT>;
            HttpOnly;
            Secure;
            Path=/;
            SameSite=Lax;
            Max-Age=900
```

---

### 1. HttpOnly

Access Token 쿠키는 다음과 같이 설정합니다.

```java
.httpOnly(true)
```

의미는 다음과 같습니다.

> 브라우저에는 쿠키를 저장하지만 JavaScript에서는 읽지 못하게 한다.

악성 스크립트가 실행되더라도 다음 코드로 토큰을 가져가기 어렵게 합니다.

```javascript
document.cookie
```

OnFilm 설정은 다음과 같습니다.

```text
access_token  → HttpOnly=true
refresh_token → HttpOnly=true
XSRF-TOKEN    → HttpOnly=false
```

#### XSRF-TOKEN은 왜 false인가?

프론트엔드가 쿠키값을 읽어 요청 헤더에 넣어야 하기 때문입니다.

```text
XSRF-TOKEN 쿠키 읽기
→ X-XSRF-TOKEN 헤더에 같은 값 설정
→ 서버가 쿠키와 헤더 비교
```

따라서 XSRF-TOKEN은 JavaScript에서 읽을 수 있어야 합니다.

---

### 2. Secure

```java
.secure(true)
```

의미는 다음과 같습니다.

> HTTPS 연결에서만 쿠키를 전송한다.

운영 환경에서 Secure가 설정되면 다음 요청에는 쿠키를 보냅니다.

```text
https://onfilm.example.com → 쿠키 전송
```

다음 일반 HTTP 요청에는 보내지 않습니다.

```text
http://onfilm.example.com → 쿠키 전송 안 함
```

네트워크 중간에서 쿠키가 평문 HTTP로 노출되는 위험을 줄입니다.

로컬 개발 환경에서는 HTTPS를 사용하지 않을 수 있어 설정값으로 구분합니다.

```java
.secure(
    authProperties.accessCookieSecure()
)
```

```text
개발 환경 → false 가능
운영 환경 → true 권장
```

---

### 3. SameSite

```java
.sameSite("Lax")
```

SameSite는 다른 사이트에서 시작된 요청에 쿠키를 보낼지를 제한합니다.

예를 들어 사용자가 OnFilm에 로그인한 상태라고 가정하겠습니다.

현재 쿠키:
access_token=...

악성 사이트가 OnFilm으로 요청을 보내려고 할 수 있습니다.

```text
evil.example
→ onfilm.example/api/...
```

SameSite는 이런 교차 사이트 요청에 쿠키가 자동으로 포함되는 범위를 제한해 CSRF 위험을 줄입니다.

주요 값은 다음과 같습니다.

#### Strict

```text
다른 사이트에서 시작된 요청에는 거의 쿠키를 보내지 않음
보안은 강하지만 사용자 이동 과정이 불편할 수 있음
```

#### Lax

```text
대부분의 교차 사이트 상태 변경 요청에는 쿠키를 제한
일반적인 링크 이동 등 일부 요청에는 허용
```

#### None

```text
교차 사이트 요청에도 쿠키 전송
Secure=true가 반드시 필요
프론트와 API가 완전히 다른 사이트일 때 필요할 수 있음
```

OnFilm의 기본값은 Lax입니다.

```java
return "Lax";
```

SameSite만으로 모든 CSRF 공격을 막는다고 생각하면 안 됩니다. OnFilm은 별도의 CSRF Token 검사도 사용합니다.

---

### 4. Path

```java
.path("/")
```

Path는 어떤 URL 요청에 쿠키를 보낼지 정합니다.

#### Access Token

```text
이름: access_token
Path: /
```

다음 요청에 모두 사용할 수 있습니다.

```text
/api/people/...
/api/movies/...
/auth/me
```

#### Refresh Token

```text
이름: refresh_token
Path: /auth
```

다음과 같은 /auth 경로에만 전송됩니다.

```text
/auth/refresh
/auth/logout
```

다음 일반 API에는 전송되지 않습니다.

```text
/api/people/...
/api/movies/...
```

Refresh Token은 일반 API 인증에 필요하지 않으므로 전송 범위를 줄인 것입니다.

#### CSRF Token

```text
이름: XSRF-TOKEN
Path: /
```

상태를 변경하는 여러 API 요청에 사용해야 하므로 전체 경로에 전송됩니다.

---

### 5. Max-Age

```java
.maxAge(
    authProperties.accessTokenTtl().toSeconds()
)
```

브라우저가 쿠키를 보관할 시간을 초 단위로 지정합니다.

```text
Access Token TTL 15분
→ Max-Age=900

Refresh Token TTL 14일
→ Max-Age=1209600
```

시간이 지나면 브라우저가 쿠키를 만료 처리합니다.

쿠키를 삭제할 때는 다음처럼 설정합니다.

```java
.maxAge(0)
```

```text
Max-Age=0
→ 브라우저가 쿠키 삭제
```

---

### 세 쿠키 설정 비교

| 옵션 | access_token | refresh_token | XSRF-TOKEN |
| --- | --- | --- | --- |
| 값 | JWT | 무작위 토큰 | 무작위 CSRF 값 |
| HttpOnly | true | true | false |
| Secure | 운영 환경 true | 운영 환경 true | 운영 환경 true |
| Path | / | /auth | / |
| SameSite | 기본 Lax | 기본 Lax | 기본 Lax |
| Max-Age | Access Token TTL | Refresh Token TTL | Access Token TTL |

---

### 각 옵션의 역할

```text
HttpOnly
→ JavaScript가 인증 토큰을 읽지 못하게 함
→ XSS 토큰 탈취 위험 감소

Secure
→ HTTPS에서만 쿠키 전송
→ 네트워크 노출 위험 감소

SameSite
→ 다른 사이트에서 시작된 요청의 쿠키 전송 제한
→ CSRF 위험 감소

Path
→ 필요한 주소에만 쿠키 전송
→ Refresh Token 노출 범위 감소

Max-Age
→ 브라우저가 쿠키를 보관할 시간 설정
```

### 한 줄 요약

> OnFilm은 HttpOnly, Secure, SameSite, Path, Max-Age를 이용해 인증 쿠키를 JavaScript와 평문 통신으로부터 보호하고, 필요한 요청에만 제한된 기간 동안 전송되도록 설정합니다.

<!-- 학습 원문 끝: lesson-15 -->

</details>

---

<a id="lesson-16"></a>

## 1-3. 쿠키 인증과 CSRF 방어

쿠키가 자동 전송되는 점을 악용한 위조 요청을 막기 위해 **요청 출처와 CSRF 쿠키·헤더의 일치 여부를 검사**합니다. JWT를 쿠키에 담아도 이 검토가 필요합니다.

```http
Cookie: access_token=...; XSRF-TOKEN=abc123
X-CSRF-TOKEN: abc123
```

현재 인증 API 일부와 내부 API는 검사에서 제외됩니다. 내부 Callback은 별도 HMAC 인증을 사용합니다. 인증 경로의 제외 범위와 스킴·포트가 엄격하지 않은 출처 비교는 보완할 부분입니다.

<details>
<summary>상세히 보기: 쿠키 자동 전송·Double Submit Cookie·출처 검사·제외 경로</summary>

<!-- 학습 원문 시작: lesson-16 -->

OnFilm은 Access Token을 쿠키에 담아 사용합니다.

쿠키는 편리하지만 다음과 같은 특징이 있습니다.

> 브라우저는 사용자의 의도와 관계없이 요청 주소에 맞는 쿠키를 자동으로 전송합니다.

이 특징을 공격에 이용하는 것이 CSRF입니다.

---

### 1. CSRF란?

CSRF는 사용자가 로그인한 상태를 악용해, 사용자가 원하지 않은 요청을 보내게 만드는 공격입니다.

예를 들어 사용자가 OnFilm에 로그인해 있다고 가정하겠습니다.

```text
access_token=로그인한 사용자의 JWT
```

이 상태에서 공격자가 만든 사이트에 접속합니다.

```text
https://attacker.example
```

공격 사이트가 브라우저를 이용해 다음 요청을 보내도록 만들 수 있습니다.

```text
DELETE https://onfilm.example/api/people/my-public-id
```

브라우저는 요청 대상이 OnFilm이므로 저장해 둔 OnFilm 쿠키를 자동으로 붙일 수 있습니다.

```http
Cookie: access_token=사용자의 JWT
```

OnFilm 서버가 쿠키만 확인하면 다음처럼 오해할 수 있습니다.

```text
유효한 Access Token이 있음
→ 로그인한 사용자의 요청이라고 판단
→ 프로필 삭제 처리
```

하지만 실제 사용자는 프로필 삭제를 요청하지 않았습니다.

> CSRF는 브라우저가 인증 쿠키를 자동으로 보내는 성질을 이용해 사용자가 원하지 않은 작업을 실행시키는 공격입니다.

---

### 2. JWT를 사용해도 CSRF가 발생하는 이유

CSRF 발생 여부는 JWT인지 아닌지보다 인증 정보를 어떻게 전달하는지와 관련이 있습니다.

#### Authorization 헤더에 직접 넣는 경우

```http
Authorization: Bearer eyJ...
```

일반적으로 공격 사이트가 이 헤더를 마음대로 만들어 넣을 수는 없습니다.

#### 쿠키에 넣는 경우

```http
Cookie: access_token=eyJ...
```

브라우저가 조건에 맞으면 쿠키를 자동으로 전송합니다.

따라서 OnFilm처럼 JWT를 쿠키에 저장하는 서비스도 CSRF 방어가 필요합니다.

> JWT를 사용해서 안전한 것이 아니라, 쿠키가 자동 전송되므로 CSRF를 별도로 방어해야 합니다.

---

### 3. OnFilm의 CSRF Token

로그인에 성공하면 서버는 인증 쿠키와 함께 CSRF 쿠키도 발급합니다.

```text
access_token=JWT
refresh_token=임의의 긴 문자열
XSRF-TOKEN=CSRF용 임의의 문자열
```

XSRF-TOKEN은 인증 정보가 아닙니다. 요청이 OnFilm 화면의 JavaScript에서 만들어졌는지 확인하는 보조 값입니다.

Access Token과 Refresh Token에는 다음 설정이 있습니다.

```text
HttpOnly=true
```

따라서 JavaScript가 값을 읽을 수 없습니다.

반면 CSRF 쿠키에는 다음과 같이 설정합니다.

```text
HttpOnly=false
```

OnFilm의 JavaScript가 값을 읽어 요청 헤더에 넣어야 하기 때문입니다.

---

### 4. 브라우저가 보내는 정상 요청

OnFilm의 auth.js는 쿠키에서 CSRF Token을 읽습니다.

```javascript
const csrf = getCsrfToken();
```

```java
if (csrf) {
    headers.set("X-CSRF-TOKEN", csrf);
}
```

그리고 요청을 보냅니다.

```javascript
fetch(url, {
    ...options,
    headers,
    credentials: "include",
});
```

credentials: "include"는 요청 조건에 맞는 쿠키를 함께 보내도록 지정한 것입니다.

결과적으로 정상 요청에는 같은 값이 두 곳에 들어갑니다.

```http
Cookie: XSRF-TOKEN=abc123
X-CSRF-TOKEN: abc123
```

서버는 쿠키 값과 헤더 값을 비교합니다.

```java
if (!csrfCookieValue.equals(csrfHeader)) {
    reject(response);
    return;
}
```

두 값이 같아야 다음 필터로 요청을 전달합니다.

```java
filterChain.doFilter(request, response);
```

이 방식을 Double Submit Cookie 방식이라고 합니다.

> 같은 CSRF Token을 쿠키와 요청 헤더에 각각 보내고, 서버에서 두 값이 같은지 확인하는 방식입니다.

---

### 5. 공격 사이트는 왜 헤더 값을 넣기 어려울까?

쿠키는 브라우저가 자동으로 전송할 수 있습니다.

하지만 다른 출처의 사이트는 OnFilm의 XSRF-TOKEN 쿠키를 읽어서 다음 헤더를 만드는 데 제약을 받습니다.

```http
X-CSRF-TOKEN: 올바른 값
```

따라서 공격 요청은 보통 다음처럼 됩니다.

```http
Cookie: access_token=JWT
Cookie: XSRF-TOKEN=abc123
```

X-CSRF-TOKEN 헤더는 없음

OnFilm 필터는 헤더가 없으므로 요청을 거부합니다.

```java
if (csrfCookieValue == null
        || csrfCookieValue.isBlank()
        || csrfHeader == null
        || csrfHeader.isBlank()) {
    reject(response);
    return;
}
```

응답은 다음과 같습니다.

```java
HTTP 403 Forbidden
```

```json
{
  "code": "CSRF_VALIDATION_FAILED",
  "message": "CSRF 검증에 실패했습니다.",
  "errors": []
}
```

---

### 6. 요청 출처도 함께 확인

OnFilm은 CSRF Token만 비교하지 않고 요청이 어느 사이트에서 시작됐는지도 확인합니다.

```java
String origin = request.getHeader("Origin");
String referer = request.getHeader("Referer");
String host = request.getHeader("Host");
```

각 값은 다음 의미입니다.

```text
Host
→ 요청을 받는 서버 주소

Origin
→ 요청을 시작한 사이트의 출처

Referer
→ 현재 요청으로 이동하기 전 페이지 주소
```

정상적인 요청은 다음과 같습니다.

```http
Host: onfilm.example
Origin: https://onfilm.example
```

두 주소가 같은 출처인지 확인합니다.

```java
if (!isSameOrigin(origin, host)
        && !isSameOrigin(referer, host)) {
    reject(response);
    return;
}
```

즉, Origin과 Referer 중 어느 것도 OnFilm과 일치하지 않으면 요청을 거부합니다.

---

### 7. 어떤 요청을 검사할까?

조회용 요청은 CSRF 검사를 건너뜁니다.

```java
private static final Set<String> SAFE_METHODS = Set.of(
    "GET",
    "HEAD",
    "OPTIONS",
    "TRACE"
);
```

이 요청들은 원칙적으로 서버 데이터를 변경하지 않아야 하기 때문입니다.

반면 다음 요청은 보통 데이터를 변경합니다.

```text
POST
PUT
PATCH
DELETE
```

예를 들어 다음 요청은 CSRF 검사를 받습니다.

```http
PUT /api/people/{publicId}
DELETE /api/people/{publicId}/gallery/{galleryId}
POST /api/people/{publicId}/filmography
```

흐름은 다음과 같습니다.

```text
상태 변경 요청
→ 요청 출처 확인
→ XSRF-TOKEN 쿠키 확인
→ X-CSRF-TOKEN 헤더 확인
→ 두 Token 값 비교
→ 모두 통과하면 다음 필터로 전달
```

---

### 8. CSRF 검사를 제외하는 경로

현재 OnFilm은 다음 인증 경로를 CSRF 검사에서 제외합니다.

```text
/auth/login
/auth/signup
/auth/refresh
/auth/logout
```

아직 로그인하지 않은 회원가입과 로그인 요청에는 CSRF 쿠키가 없을 수 있기 때문입니다.

내부 Worker 요청도 제외합니다.

```text
/internal/api/**
```

Worker는 브라우저가 아니므로 브라우저용 쿠키와 CSRF Token을 사용하지 않습니다.

그렇다고 내부 API의 보안을 없앤 것은 아닙니다.

```text
사용자 브라우저 요청
→ CSRF Token으로 보호

Worker Callback
→ HMAC 서명으로 보호
```

요청을 보내는 주체에 따라 다른 보호 방식을 적용한 것입니다.

---

### 9. Spring 기본 CSRF를 끈 이유

SecurityConfig에는 다음 코드가 있습니다.

```java
.csrf(AbstractHttpConfigurer::disable);
```

이 코드만 보면 CSRF 방어를 하지 않는 것처럼 보일 수 있습니다.

하지만 OnFilm은 Spring Security의 기본 CSRF 기능 대신 직접 만든 필터를 사용합니다.

```java
.addFilterBefore(
    csrfProtectionFilter,
    UsernamePasswordAuthenticationFilter.class
)
```

따라서 정확한 설명은 다음과 같습니다.

> Spring Security의 기본 CSRF 기능은 끄고, OnFilm의 쿠키 구성과 내부 API 구조에 맞춘 CsrfProtectionFilter를 사용합니다.

---

### 10. 전체 정상 흐름

```text
로그인 성공
→ 서버가 XSRF-TOKEN 쿠키 발급
→ 브라우저가 쿠키 저장
→ 화면 JavaScript가 XSRF-TOKEN을 읽음
→ X-CSRF-TOKEN 헤더에 같은 값을 넣음
→ 상태 변경 요청 전송
→ 서버가 Origin 또는 Referer 확인
→ CSRF 쿠키와 헤더 비교
→ 같으면 요청 허용
```

실패 흐름은 다음과 같습니다.

```text
외부 사이트에서 상태 변경 요청 유도
→ 인증 쿠키는 자동 전송될 수 있음
→ 하지만 올바른 CSRF 헤더가 없음
→ CsrfProtectionFilter가 요청 거부
→ HTTP 403
```

### 한 줄 요약

> OnFilm은 상태를 변경하는 브라우저 요청에서 출처를 확인하고, XSRF-TOKEN 쿠키와 X-CSRF-TOKEN 헤더가 같은지 검사해 위조 요청을 차단합니다.

<!-- 학습 원문 끝: lesson-16 -->

</details>

---

<a id="lesson-17"></a>

## 1-4. 인증과 인가, 프로필 소유권 검사

**인증은 누구인지 확인하고, 인가는 그 작업을 허용할지 판단**합니다. 프로필 수정은 로그인 확인 후에도 소유권을 검사해야 합니다.

```java
Person person = currentPersonProvider.getRequired(publicId);
// 현재 사용자의 Person.publicId와 요청한 publicId를 비교
// 다르면 ForbiddenPersonAccessException → 403
```

publicId는 공유 가능한 식별값입니다. 주소를 알고 있다는 사실만으로 수정 권한이 생기지 않으므로 서버가 로그인 정보에서 얻은 소유자와 비교합니다.

<details>
<summary>상세히 보기: authenticated()를 통과해도 다른 사람의 프로필을 못 고치는 이유</summary>

<!-- 학습 원문 시작: lesson-17 -->

• 다음은 인증과 인가의 차이, 그리고 OnFilm에서 다른 사람의 프로필 수정을 어떻게 막는지입니다.

로그인했다고 해서 모든 사람의 정보를 수정할 수 있는 것은 아니죠. 서버는 두 가지를 구분해서 확인합니다.

| 구분 | 확인하는 내용 | 예시 |
| --- | --- | --- |
| 인증 | 누구의 요청인가? | 철수가 로그인한 상태인가? |
| 인가 | 이 작업을 해도 되는가? | 철수가 영희의 프로필을 수정해도 되는가? |

### 1. 먼저 JWT로 요청한 사용자를 확인합니다.

앞서 배운 JwtAuthFilter가 Access Token을 검증합니다.

```java
if (token != null
        && !token.isBlank()
        && jwtProvider.validate(token)) {

    Authentication authentication =
            jwtProvider.getAuthentication(token);
```

```javascript
    SecurityContextHolder.getContext()
            .setAuthentication(authentication);
}
```

유효한 토큰이면 현재 요청의 인증 정보를 SecurityContext에 넣습니다.

예를 들어 철수의 토큰이라면 서버는 다음 정보를 알게 됩니다.

현재 요청을 보낸 사용자: userId = 10

이 단계에서는 아직 “어느 프로필을 수정할 수 있는지”까지 확인하지 않습니다.

### 2. SecurityConfig에서 로그인이 필요한 주소인지 확인합니다.

OnFilm 설정에는 다음 규칙이 있습니다.

```java
.requestMatchers(
    HttpMethod.GET,
    "/api/people/*"
).permitAll()

.requestMatchers("/api/people/**")
.authenticated()
```

두 설정은 다음 뜻입니다.

```http
GET /api/people/abc
→ 프로필 조회
→ 로그인하지 않아도 접근 가능

PUT /api/people/abc
→ 프로필 수정
→ 로그인한 사용자만 접근 가능
```

설정은 나열된 순서대로 확인해 처음 일치하는 규칙을 적용합니다. 그래서 공개 조회 규칙을 먼저 둡니다.

여기서 authenticated()가 보장하는 것은 로그인 여부입니다. 수정할 프로필의 주인인지 확인하는 과정은 추가로 필요합니다.

### 3. Service에서 프로필 소유자를 확인합니다.

프로필 수정 메서드의 첫 줄을 보겠습니다.

```java
public void updateProfile(
        String publicId,
        UpdatePersonRequest request
) {
    Person person =
            currentPersonProvider.getRequired(publicId);
```

```javascript
    applyProfile(
        person,
        request.name(),
        request.birthDate(),
        request.birthPlace(),
        request.oneLineIntro(),
        request.profileImageKey(),
        request.snsList(),
        request.rawTags()
    );
}
```

다음 코드가 소유권 확인을 담당합니다.

```javascript
currentPersonProvider.getRequired(publicId);
```

내부에서는 먼저 로그인한 사용자에게 연결된 프로필을 가져옵니다.

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
```

순서대로 읽으면 이렇습니다.

```text
현재 요청의 인증 정보에서 userId 확인
→ 해당 User 조회
→ User에 연결된 Person 조회
→ 현재 사용자의 Person 반환
```

그다음 요청 주소의 publicId와 내 프로필의 publicId를 비교합니다.

```java
public Person getRequired(String publicId) {
    Person person = getRequired();

    if (!Objects.equals(person.getPublicId(), publicId)) {
        throw new ForbiddenPersonAccessException();
    }

    return person;
}
```

Objects.equals(a, b)는 두 값이 같은지 비교합니다. null이 들어와도 안전하게 비교할 수 있습니다.

### 4. 철수가 영희의 프로필 수정을 시도하면

다음과 같은 상황이라고 가정하겠습니다.

```text
로그인한 사람: 철수
철수의 publicId: chulsoo-123
영희의 publicId: younghee-456
```

철수가 요청 주소를 바꿔 전송합니다.

```http
PUT /api/people/younghee-456
```

철수의 토큰 자체는 유효하므로 로그인 검사는 통과합니다. 다른 요청 검증도 통과했다고 가정하면, Service에서 다음 비교를 수행합니다.

```text
Objects.equals("chulsoo-123", "younghee-456")
```

결과가 false이므로 예외가 발생합니다.

```java
throw new ForbiddenPersonAccessException();
```

프로필 변경 코드까지 진행되지 않고, HTTP 403 Forbidden을 반환합니다.

반대로 본인의 주소라면 비교를 통과한 뒤 프로필을 수정합니다.

```http
PUT /api/people/chulsoo-123
```

여기서 기억할 부분은 publicId를 알고 있다는 사실이 수정 권한을 의미하지는 않는다는 것입니다. 프로필 주소는 다른 사람에게 공유할 수 있지만, 수정할 때는 로그인 정보에서 확인한 소유자와 일치해야 합니다.

직접 코드를 따라볼 때는 PersonCommandService (src/main/java/com/onfilm/domain/movie/service/PersonCommandService.java)의
updateProfile()에서 CurrentPersonProvider (src/main/java/com/onfilm/domain/movie/service/CurrentPersonProvider.java)의
getRequired(publicId)로 이동해 보세요. 이 연결이 “내 프로필만 수정할 수 있다”는 규칙을 구현한 부분입니다.

<!-- 학습 원문 끝: lesson-17 -->

</details>

---

<a id="lesson-18"></a>

## 1-5. 프론트엔드의 자동 재발급과 요청 재시도

자동 재발급 함수를 사용하는 요청은 **실패 응답 → 재발급 → 원래 요청 한 번 재시도**로 이어집니다. 사용자가 다시 비밀번호를 입력하지 않고 서비스를 이어가도록 합니다.

```javascript
const token = await refreshAccessToken();
if (!token) {
    return res;
}
return apiFetch(url, options);
```

여기서 token은 JWT가 아니라 성공 시 반환되는 "ok"입니다. 실제 토큰은 쿠키로 교체됩니다. 현재 403에도 재발급을 시도하지만 소유권 부족은 해결되지 않으며 일반 fetch에 자동 적용되는 기능도 아닙니다.

<details>
<summary>상세히 보기: 401·403 처리부터 쿠키 교체와 원래 요청 재시도까지</summary>

<!-- 학습 원문 시작: lesson-18 -->

Access Token이 만료돼도 Refresh Token이 유효하다면, 사용자가 비밀번호를 다시 입력하지 않고 서비스를 계속 이용할 수 있습니다.

이 과정을 연결하는 코드가 src/main/resources/static/js/auth.js의 apiFetchWithAutoRefresh()입니다.

### 1. 먼저 원래 요청을 보냅니다.

예를 들어 사용자가 프로필을 수정한다고 생각해 보겠습니다.

```javascript
OnfilmAuth.apiFetchWithAutoRefresh(
    "/api/people/my-public-id",
    {
        method: "PUT",
        body: JSON.stringify({
            // 수정할 프로필 정보
        }),
    }
);
```

함수 내부에서는 먼저 요청을 한 번 보냅니다.

```javascript
let res = await apiFetch(url, options);
```

여기서 각 값은 다음 의미입니다.

- url: 요청할 주소
- options: HTTP 메서드, 수정할 데이터 등의 요청 설정
- res: 서버가 보낸 응답
- await: 요청 결과가 도착할 때까지 이 함수의 다음 처리를 기다림

apiFetch()에는 다음 설정이 있습니다.

```javascript
credentials: "include"
```

따라서 브라우저가 전송 조건에 맞는 인증 쿠키를 함께 보냅니다.

### 2. 서버의 응답을 확인합니다.

```java
if (res.status !== 401 && res.status !== 403) {
    return res;
}
```

현재 코드는 응답이 401이나 403이 아니면 그대로 반환합니다.

예를 들어 200이면 정상적으로 처리됐으므로 재발급할 필요가 없습니다.

Access Token이 만료됐거나 없다면 인증이 필요한 요청은 401을 받을 수 있습니다. 그러면 다음 재발급 단계로 넘어갑니다.

### 3. Refresh Token으로 새 토큰을 요청합니다.

```javascript
const token = await refreshAccessToken();
```

refreshAccessToken() 안에서는 다음 요청을 보냅니다.

```javascript
const res = await apiFetch(
    "/auth/refresh",
    { method: "POST" }
);
```

Refresh Token은 HttpOnly 쿠키이므로 JavaScript가 값을 직접 읽지 않습니다. 브라우저가 요청에 쿠키를 붙여서 전송합니다.

```http
POST /auth/refresh
Cookie: refresh_token=기존RefreshToken
```

서버에서는 앞서 배운 과정이 실행됩니다.

```text
Refresh Token 검사
→ 기존 Refresh Token 사용 처리
→ 새 Refresh Token 생성·저장
→ 새 Access Token 생성
→ 응답의 Set-Cookie로 새 쿠키 전달
```

브라우저는 응답을 받고 기존 쿠키를 새 값으로 바꿉니다.

참고로 현재 함수는 성공하면 JWT 문자열 대신 "ok"를 반환합니다.

```javascript
return "ok";
```

따라서 호출부의 const token은 이름과 달리, 여기서는 재발급 성공 여부를 판단하는 값으로 쓰이고 있습니다. 실제 인증 토큰은 쿠키에 들어 있습니다.

### 4. 원래 요청을 다시 보냅니다.

재발급이 성공하면 다음 코드가 실행됩니다.

```javascript
return apiFetch(url, options);
```

처음 요청했던 주소와 데이터를 그대로 사용합니다. 브라우저에는 새 쿠키가 있으므로 이번에는 새 Access Token이 전송됩니다.

```text
프로필 수정 요청
→ 인증 실패 응답
→ Refresh Token으로 재발급
→ 브라우저의 인증 쿠키 교체
→ 같은 프로필 수정 요청 재전송
→ 정상 처리
```

사용자 입장에서는 저장이 조금 늦어질 수 있지만, 다시 로그인할 필요는 없는 것입니다.

### 5. 재발급까지 실패하면 어떻게 될까?

Refresh Token도 만료됐거나 폐기됐다면 재발급이 실패합니다.

```java
if (!token) {
    return res;
}
```

이때는 원래 요청의 오류 응답을 돌려줍니다. 이 함수를 호출한 화면 코드가 응답을 확인해 로그인 안내나 화면 이동을 처리해야 합
니다.

또한 재시도할 때는 다음처럼 일반 요청 함수를 사용합니다.

```javascript
return apiFetch(url, options);
```

자동 재발급 함수를 다시 호출하지 않으므로, 재발급과 재시도가 무한히 반복되지는 않습니다.

### 6. 현재 구현에서 구분해서 알아둘 부분

현재 코드는 401뿐 아니라 403에도 재발급을 시도합니다. 하지만 앞서 배운 것처럼 403은 다른 사람의 프로필을 수정하는 등 권한이 없는 상황에서도 발생합니다.

그 경우에는 새 토큰을 받아도 권한이 생기지 않으므로 재시도 역시 실패합니다. 현재 동작을 설명할 때는 “401·403에 재발급을 한번 시도한다”고 이해하면 됩니다.

그리고 이 기능은 apiFetchWithAutoRefresh()를 통해 보낸 요청에 적용됩니다. 일반 fetch()나 apiFetch()로 보낸 모든 요청에 자동으로 적용되는 것은 아닙니다.

다음 1-6에서는 여러 요청이 동시에 인증 실패를 받았을 때, 재발급 요청을 하나로 모으는 refreshInFlight를 살펴보겠습니다.

<!-- 학습 원문 끝: lesson-18 -->

</details>

---

<a id="lesson-19"></a>

## 1-6. 동시에 발생한 토큰 재발급 요청을 하나로 모으기

여러 보호된 요청이 동시에 인증 실패를 받으면 재발급도 겹칠 수 있습니다. **이미 진행 중인 재발급의 Promise를 공유**해 현재 페이지의 겹치는 요청을 줄입니다.

```javascript
if (refreshInFlight) {
    return refreshInFlight;
}
// 첫 호출이 재발급을 시작하고 Promise를 보관
// 완료 후 finally에서 refreshInFlight = null
```

원래 API 요청은 각각 재시도하고 재발급 작업만 공유합니다. 다른 탭이나 완료 뒤 늦게 들어온 실패 응답까지 모두 조정하지 않으므로 서버의 동시성 방어도 필요합니다. 원문의 프로필·갤러리 조회는 여러 요청의 예시이며 실제로는 공개 API입니다.

<details>
<summary>상세히 보기: 화면 하나의 여러 요청·Promise 공유·finally에서 null로 바꾸는 이유</summary>

<!-- 학습 원문 시작: lesson-19 -->

화면 하나를 열어도 서버에는 여러 요청이 나갈 수 있습니다.

```text
프로필 조회
필모그래피 조회
갤러리 조회
```

이때 인증이 필요한 여러 요청이 동시에 토큰 만료로 실패하면, 각 요청이 따로 재발급을 시도할 수 있습니다.

OnFilm은 refreshInFlight를 이용해 이미 재발급 중이면 그 결과를 함께 기다리도록 구현했습니다.

### 1. 각자 재발급하면 왜 문제가 될까?

이전에 배운 Refresh Token Rotation을 떠올려 보겠습니다.

```text
기존 Refresh Token 사용
→ 기존 토큰 폐기
→ 새로운 Refresh Token 발급
```

여러 요청이 같은 Refresh Token으로 동시에 재발급을 요청하면 다음 상황이 생길 수 있습니다.

```text
A 요청 → 기존 Refresh Token으로 재발급 성공
B 요청 → 같은 토큰으로 재발급 시도 → 충돌 또는 재사용으로 거부
C 요청 → 같은 토큰으로 재발급 시도 → 충돌 또는 재사용으로 거부
```

정상 사용자가 화면을 이용했을 뿐인데 인증 오류가 발생할 수 있는 것입니다.

### 2. 진행 중인 재발급 작업을 변수에 보관합니다.

```javascript
let refreshInFlight = null;
```

in flight는 여기서 “진행 중”이라는 의미입니다.

```text
null
→ 현재 진행 중인 재발급이 없음

Promise가 들어 있음
→ 재발급을 진행 중이며 결과를 기다릴 수 있음
```

Promise는 나중에 완료될 작업의 결과를 받을 수 있는 객체입니다. 토큰 자체를 저장하는 변수는 아닙니다.

### 3. 첫 번째 요청만 재발급을 시작합니다.

실제 코드의 핵심 부분입니다.

```javascript
async function refreshAccessToken() {
    if (refreshInFlight) {
        return refreshInFlight;
    }

    refreshInFlight = (async () => {
        const res = await apiFetch(
            AUTH.refresh,
            { method: "POST" }
        );
```

```java
        if (res.status !== 200) {
            return null;
        }

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

처음 호출됐을 때는 refreshInFlight가 null입니다.

따라서 재발급 요청을 시작하고, 그 작업의 Promise를 변수에 보관합니다.

```javascript
refreshInFlight = (async () => {
    // 재발급 요청
})();
```

여기서 마지막 ()는 앞에서 만든 함수를 즉시 실행한다는 뜻입니다.

### 4. 뒤이어 들어온 요청은 같은 결과를 기다립니다.

첫 번째 재발급이 끝나기 전에 다른 요청이 이 함수를 호출하면 다음 조건에 걸립니다.

```javascript
if (refreshInFlight) {
    return refreshInFlight;
}
```

이미 진행 중인 작업이 있으므로 새 재발급 요청을 보내지 않습니다. 기존 작업의 결과를 함께 기다립니다.

```text
A → 재발급 시작 ───────────┐
B → 진행 중인 재발급 기다림 ├→ 재발급 완료
C → 진행 중인 재발급 기다림 ┘
```

재발급이 성공하면 브라우저의 쿠키가 바뀝니다. 이후 각 요청은 앞서 배운 함수에서 자신의 원래 요청을 다시 보냅니다.

```text
A → 새 쿠키로 원래 요청 재시도
B → 새 쿠키로 원래 요청 재시도
C → 새 쿠키로 원래 요청 재시도
```

공유하는 것은 재발급 작업이고, 원래 요청들은 각자 다시 실행합니다.

### 5. 끝나면 왜 null로 바꿀까?

```javascript
finally {
    refreshInFlight = null;
}
```

finally는 작업이 성공하거나 실패해도 실행됩니다.

재발급이 끝났으므로 “진행 중인 작업이 없다”는 상태로 되돌리는 것입니다. 그래야 나중에 토큰이 다시 만료됐을 때 새로운 재발급
을 시작할 수 있습니다.

이 값이 계속 남아 있으면 이후에도 이미 끝난 작업의 결과만 가져오게 됩니다.

다만 이 변수는 현재 페이지의 JavaScript 안에서만 공유됩니다. 서로 다른 브라우저 탭까지 묶어 주지는 않습니다. 또한 재발급이
끝난 뒤 늦게 도착한 인증 실패 응답은 별도 재발급을 시작할 수 있습니다.

그래서 프론트엔드는 겹치는 재발급 요청을 줄이고, 서버의 @Version과 재사용 검사는 여전히 토큰 상태를 보호하는 역할을 합니다.

<!-- 학습 원문 끝: lesson-19 -->

</details>

---

<a id="lesson-20"></a>

## 1-7. 만료된 Refresh Token의 사용 차단과 DB 정리

만료된 토큰은 요청 검사에서 사용할 수 없게 합니다. DB 기록은 남겨 두었다가 **만료 또는 폐기로부터 보관 기간이 지난 뒤** 정리합니다.

```java
Instant cutoff = clock.instant().minus(retention);
int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
```

기본값은 30일 보관·매일 오전 4시 정리입니다. 30일은 추가 사용 기간이 아니며 발급일 기준도 아닙니다. 시간대는 스케줄러 기본값에 따르고 서버가 실행 중이어야 합니다.

<details>
<summary>상세히 보기: 토큰 사용 차단과 30일 뒤 기록 삭제는 어떻게 다를까?</summary>

<!-- 학습 원문 시작: lesson-20 -->

Refresh Token이 만료되면 재발급에 사용할 수 없지만, DB 기록이 그 순간 자동으로 삭제되지는 않습니다.

OnFilm에서는 사용 가능 여부를 검사하는 코드와 오래된 기록을 삭제하는 코드가 따로 있습니다.

### 1. 만료 여부는 어떻게 확인할까?

RefreshToken에는 만료 시각이 저장돼 있습니다.

```java
private Instant expiresAt;
```

예를 들어 다음과 같습니다.

```text
만료 시각: 9월 16일 오후 3시
현재 시각: 9월 16일 오후 4시
→ 만료된 토큰
```

실제 검사 코드는 다음과 같습니다.

```java
public boolean isExpiredAt(Instant instant) {
    Instant requiredInstant = require(instant, "instant");
    return !requiredInstant.isBefore(expiresAt);
}
```

isBefore()는 “이전인가?”라는 뜻입니다.

앞에 !가 있으므로 다음 의미가 됩니다.

```text
현재 시각이 만료 시각보다 이전이 아니다
→ 만료 시각과 같거나 이미 지났다
→ 만료됨
```

### 2. 만료된 토큰으로 재발급을 요청하면

RefreshTokenService.rotate()에서 만료 여부를 확인합니다.

```java
if (existing.isExpiredAt(now)) {
    securityTransactionService.recordExpiredUse(
        existing.getId(),
        now
    );

    throw new InvalidRefreshTokenException();
}
```

처리 순서는 다음과 같습니다.

```text
DB에서 Refresh Token 조회
→ 만료 확인
→ 사용 시도 시각과 폐기 시각 기록
→ 재발급 거부
```

기록을 남기는 메서드는 별도 트랜잭션을 사용합니다.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordExpiredUse(Long tokenId, Instant usedAt) {
    RefreshToken token = refreshTokenRepository.findById(tokenId)
            .orElseThrow(InvalidRefreshTokenException::new);
```

```javascript
    token.revoke(usedAt);
    refreshTokenRepository.saveAndFlush(token);
}
```

앞서 배운 것처럼, 재발급 거부 예외로 바깥 트랜잭션이 롤백돼도 사용 시도 기록은 남기기 위한 구조입니다.

### 3. 사용할 수 없는 토큰을 왜 DB에 남길까?

특히 Rotation으로 이미 사용한 토큰은 기록이 있어야 재사용을 알아볼 수 있습니다.

```text
기존 토큰으로 정상 재발급
→ 기존 토큰의 revokedAt 기록
→ 같은 토큰이 다시 들어옴
→ 이미 사용한 토큰임을 확인
→ 재사용 대응
```

기록을 바로 삭제하면 해당 토큰이 다시 들어와도 “이미 사용한 토큰”인지 알 수 없습니다. DB에 없는 토큰으로 거부할 수는 있지만,
어느 사용자의 토큰이었는지 찾아 추가 대응하기 어려워집니다.

다만 기록을 계속 쌓아 두면 DB 데이터가 늘어나므로, 일정 시간이 지난 기록은 정리합니다.

### 4. 오래된 기록은 정해진 시간에 삭제합니다.

RefreshTokenCleanupService (src/main/java/com/onfilm/domain/token/service/RefreshTokenCleanupService.java)에 다음 코드가 있
습니다.

```java
@Scheduled(cron = "${auth.refresh-token-cleanup-cron:0 0 4 * * *}")
@Transactional
public int deleteOldTokens() {
    // 오래된 토큰 삭제
}
```

@Scheduled는 정해진 시간에 Spring이 메서드를 실행하도록 하는 애너테이션입니다.

현재 기본 설정은 다음과 같습니다.

```yaml
refresh-token-retention: 30d
refresh-token-cleanup-cron: "0 0 4 * * *"
```

- 보관 기간: 30일
- 정리 실행: 매일 오전 4시

시간대를 별도로 지정하지 않았으므로 오전 4시는 스케줄러가 사용하는 기본 시간대 기준입니다. 서버가 실행 중이어야 작업도 실행됩니다.

### 5. 어떤 기록을 삭제할까?

먼저 삭제 기준 시각을 계산합니다.

```java
Instant cutoff = clock.instant().minus(retention);
```

각 값의 의미는 다음과 같습니다.

```text
clock.instant() → 현재 시각
retention       → 보관 기간 30일
cutoff          → 현재 시각에서 30일 전
```

그다음 다음 조건으로 삭제합니다.

```sql
delete from RefreshToken rt
where rt.expiresAt <= :cutoff
   or (rt.revokedAt is not null and rt.revokedAt <= :cutoff)
```

다음 중 하나라도 해당하면 삭제 대상입니다.

- 만료 시각으로부터 30일 이상 지난 토큰
- 폐기 시각으로부터 30일 이상 지난 토큰

발급한 지 30일이 지났다는 뜻은 아닙니다. 만료·폐기 시각을 기준으로 계산합니다.

예를 들어 보관 기간이 30일이면:

| 토큰 상태 | 정리 대상 |
| --- | --- |
| 어제 만료됨 | 아직 보관 |
| 31일 전에 만료됨 | 삭제 |
| 31일 전에 폐기됨 | 삭제 |
| 아직 유효하고 폐기되지 않음 | 유지 |

DB에서 삭제되기 전이라도 만료된 토큰은 재발급 검사에서 거부됩니다. 30일은 추가로 사용할 수 있는 기간이 아니라, 사용할 수 없
게 된 토큰의 기록을 보관하는 기간입니다.

다음 1-8에서는 이 동작들을 테스트 코드가 어떻게 확인하는지 살펴보겠습니다.

<!-- 학습 원문 끝: lesson-20 -->

</details>

---

<a id="lesson-21"></a>

## 1-8. 토큰 관리 테스트 읽기

테스트는 **상황 준비 → 기능 실행 → 기대 결과 확인** 순서로 읽습니다. 객체의 규칙, Service의 호출, 실제 DB 저장 결과를 구분해서 봅니다.

```java
token.consume(usedAt);
assertThat(token.getRevokedAt()).isEqualTo(usedAt);

assertThatThrownBy(() -> token.consume(usedAt.plusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
```

위 코드는 각각의 검증 방식 예시이며 준비 상태에 따라 기대 결과가 달라집니다. Mock의 삭제 호출 확인은 DB 삭제 증명과 다르고, 기본 H2 테스트는 MySQL 운영 동작 전체를 증명하지 않습니다. 동시성 테스트는 요청이 실제로 겹친 순서도 중요합니다.

<details>
<summary>상세히 보기: assertThat·assertThatThrownBy·verify와 DB·동시 요청 테스트</summary>

<!-- 학습 원문 시작: lesson-21 -->

지금까지는 “코드가 어떻게 동작하는가”를 배웠습니다. 이번에는 그 코드가 의도대로 동작하는지 어떻게 확인했는가를 보겠습니다.

테스트도 일반 코드처럼 읽으면 됩니다. 다만 다음 세 가지를 먼저 찾으면 이해하기 쉬워요.

준비: 어떤 상황을 만들었는가?
실행: 어떤 기능을 호출했는가?
확인: 어떤 결과가 나와야 하는가?

### 1. 만료 시각에 정확히 도달하면 만료인가?

RefreshTokenTest (src/test/java/com/onfilm/domain/token/entity/RefreshTokenTest.java)에 있는 테스트입니다.

```java
@Test
void expiration_includesExactExpirationInstant() {
    RefreshToken token = RefreshToken.issue(
        1L,
        TOKEN_HASH,
        ISSUED_AT,
        EXPIRES_AT
    );
```

```javascript
    assertThat(token.isExpiredAt(EXPIRES_AT.minusNanos(1)))
            .isFalse();

    assertThat(token.isExpiredAt(EXPIRES_AT))
            .isTrue();

    assertThat(token.isExpiredAt(EXPIRES_AT.plusNanos(1)))
            .isTrue();
}
```

@Test는 테스트 도구가 실행할 메서드라는 표시입니다.

우선 만료 시각이 정해진 토큰을 만듭니다.

```java
RefreshToken token = RefreshToken.issue(...);
```

그다음 세 시점을 검사합니다.

| 검사 시각 | 기대하는 결과 |
| --- | --- |
| 만료 1나노초 전 | 아직 유효함 |
| 정확히 만료 시각 | 만료됨 |
| 만료 1나노초 후 | 만료됨 |

다음 문장은 이렇게 읽으면 됩니다.

```javascript
assertThat(token.isExpiredAt(EXPIRES_AT)).isTrue();
```

> 만료 시각에 검사했을 때, 만료 여부가 true인지 확인한다.

결과가 false라면 테스트가 실패합니다. 이렇게 경계 시점까지 검사하면 >와 >=를 잘못 쓰는 실수를 발견할 수 있습니다.

이 테스트는 자바 객체의 메서드를 직접 실행합니다. DB나 서버를 띄울 필요가 없습니다.

### 2. 이미 사용한 토큰을 다시 사용하면 거부하는가?

같은 파일에 다음 테스트가 있습니다.

```java
@Test
void consume_marksLastUseAndRevocationTogether() {
    RefreshToken token = RefreshToken.issue(
        1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT
    );
    Instant usedAt = ISSUED_AT.plusSeconds(30);
```

```javascript
    token.consume(usedAt);

    assertThat(token.getLastUsedAt()).isEqualTo(usedAt);
    assertThat(token.getRevokedAt()).isEqualTo(usedAt);

    assertThatThrownBy(
        () -> token.consume(usedAt.plusSeconds(1))
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("refresh token already revoked");
}
```

준비하는 상황은 다음과 같습니다.

```text
유효한 토큰 생성
→ 발급 후 30초에 한 번 사용
→ 1초 뒤 같은 토큰을 다시 사용
```

첫 번째 사용에서는 두 값이 기록돼야 합니다.

```text
lastUsedAt = 사용 시각
revokedAt  = 사용 시각
```

그리고 두 번째 사용에서는 예외가 발생해야 합니다.

```javascript
assertThatThrownBy(() -> token.consume(...))
    .isInstanceOf(IllegalStateException.class);
```

이 문장은 다음 의미입니다.

> 괄호 안의 코드를 실행했을 때 IllegalStateException이 발생하는지 확인한다.

테스트에서는 예상한 예외가 발생하면 성공입니다. 오히려 이미 사용한 토큰을 다시 사용할 수 있다면 실패입니다.

### 3. Service가 필요한 후속 처리를 호출하는가?

Entity가 자신의 상태 변경을 검사한다면, Service는 재사용 탐지 후 사용자 토큰 전체 삭제 같은 흐름을 연결합니다.

RefreshTokenServiceTest (src/test/java/com/onfilm/domain/token/service/RefreshTokenServiceTest.java)에서는 다음 내용을 확인
합니다.

```javascript
assertThatThrownBy(
    () -> refreshTokenService.rotate(OLD_RAW_TOKEN, TTL)
)
    .isInstanceOf(RefreshTokenReuseDetectedException.class);

verify(securityTransactionService)
        .deleteAllSessionsForReuse(1L);

verify(jwtProvider, never()).createRefreshToken();
```

이 코드 앞에서는 이미 폐기된 토큰을 조회 결과로 돌려주도록 준비합니다.

확인하는 것은 세 가지입니다.

- 재사용 탐지 예외가 발생하는가?
- 해당 사용자의 토큰 전체 삭제를 요청하는가?
- 새 Refresh Token을 발급하지 않는가?

여기서 verify()는 해당 메서드가 호출됐는지 확인합니다.

```javascript
verify(securityTransactionService)
        .deleteAllSessionsForReuse(1L);
```

> 사용자 1번의 토큰을 모두 삭제하라는 메서드를 호출했는가?

never()는 호출하지 않았는지 확인합니다.

```javascript
verify(jwtProvider, never()).createRefreshToken();
```

> 잘못된 재발급 요청이므로 새 토큰을 만들지 않았는가?

이 테스트의 Repository 등은 @Mock으로 만든 대역입니다. 원하는 응답을 돌려주도록 설정해 Service의 판단과 호출을 확인합니다.

따라서 삭제 메서드를 호출했다는 것은 확인하지만, DB에서 실제로 삭제됐다는 것까지 확인하는 테스트는 아닙니다.

### 4. 실패해도 DB 기록이 정말 남는가?

실제 저장 결과는 RefreshTokenExpirationPersistenceTest (src/test/java/com/onfilm/domain/token/
RefreshTokenExpirationPersistenceTest.java)에서 확인합니다.

이 테스트는 @SpringBootTest로 Spring을 띄우고, 테스트용 DB에 만료된 토큰을 저장합니다.

그다음 재발급을 시도합니다.

```javascript
assertThatThrownBy(
    () -> refreshTokenService.rotate(
        rawToken,
        Duration.ofDays(14)
    )
)
    .isInstanceOf(InvalidRefreshTokenException.class);
```

만료됐으므로 예외가 발생해야 합니다.

여기서 끝내지 않고 DB에서 토큰을 다시 조회합니다.

```java
RefreshToken reloaded =
        refreshTokenRepository.findById(tokenId)
                .orElseThrow();
```

```javascript
assertThat(reloaded.getRevokedAt()).isNotNull();

assertThat(reloaded.getLastUsedAt())
        .isEqualTo(reloaded.getRevokedAt());
```

확인하려는 내용은 이것입니다.

```text
재발급은 실패했음
그런데 DB를 다시 읽어 보니
사용 시도 시각과 폐기 시각은 남아 있음
```

앞서 배운 REQUIRES_NEW가 실제 트랜잭션 흐름에서도 의도대로 작동하는지 확인하는 테스트입니다.

### 5. 동시에 재발급하면 하나만 성공하는가?

RefreshTokenConcurrencyTest (src/test/java/com/onfilm/domain/token/RefreshTokenConcurrencyTest.java)는 두 스레드가 같은
Refresh Token으로 재발급을 요청하도록 만듭니다.

CountDownLatch라는 대기 장치를 사용해 두 요청을 준비시킨 뒤 함께 출발시킵니다.

테스트에서 기대하는 결과는 다음과 같습니다.

```javascript
assertThat(statuses)
        .containsExactlyInAnyOrder(200, 401);

assertThat(statuses).doesNotContain(500);
```

> 두 응답 중 하나는 성공이고, 다른 하나는 인증 실패여야 한다. 예상하지 못한 서버 오류가 나면 안 된다.

DB에는 폐기되지 않은 토큰이 하나 남는지도 확인합니다.

다만 함께 출발시켜도 DB를 읽는 순간까지 완전히 같아지는 것은 아닙니다. 한 요청이 기존 토큰의 폐기 완료 후 조회하면 재사용 탐
지 경로로 갈 수 있으므로, 동시성 테스트는 요청이 실제로 어떤 순서로 겹쳤는지도 중요합니다.

이번에는 테스트 코드를 읽어 검증 의도를 살펴봤습니다. 테스트를 새로 실행한 것은 아닙니다.

이제 다음 1-9에서 로그인부터 재발급·로그아웃까지 연결하고, 현재 구현의 한계를 정리하면 이 단원을 마무리할 수 있습니다.

<!-- 학습 원문 끝: lesson-21 -->

</details>

---

<a id="lesson-22"></a>

## 1-9. 로그인부터 로그아웃까지 전체 흐름과 현재 구현의 한계

회원가입으로 계정·프로필을 만들고, 로그인으로 토큰을 발급합니다. **API 인증은 Access Token, 로그인 연장은 Refresh Token, 브라우저 상태 변경 요청 보호는 CSRF 검사가 담당**합니다.

```text
회원가입 → 로그인 → 쿠키 저장 → 보호된 API 요청
→ Access 만료 시 재발급 → 새 쿠키로 요청 재시도
→ 로그아웃 → Refresh 폐기·쿠키 삭제
```

로그아웃 후 복사된 Access JWT의 유효기간, 정상 중복 요청의 전체 토큰 폐기, 페이지 범위의 재발급 공유, 403 재시도와 CSRF 제외 정책을 현재 구현의 한계로 함께 기억합니다.

<details>
<summary>상세히 보기: 전체 요청 흐름과 현재 구현의 한계 다시 연결하기</summary>

<!-- 학습 원문 시작: lesson-22 -->

지금까지 배운 내용을 사용자 한 명이 로그인하고, 서비스를 이용하다가 로그아웃하는 과정으로 연결해 보겠습니다.

### 1. 로그인하면 서버가 쿠키를 발급합니다.

사용자가 이메일과 비밀번호를 입력합니다.

```http
POST /auth/login
```

서버는 DB에서 사용자를 찾고, BCrypt로 비밀번호가 일치하는지 확인합니다.

인증에 성공하면 다음 값을 생성합니다.

| 발급 값 | 역할 | 보관 위치 |
| --- | --- | --- |
| Access Token | 일반 요청에서 사용자 확인 | 브라우저 쿠키 |
| Refresh Token | 새 인증 토큰 발급 | 원문은 쿠키, 해시와 상태는 DB |
| CSRF Token | 상태 변경 요청의 위조 방어 | 브라우저 쿠키 |

서버가 응답의 Set-Cookie로 전달하면 브라우저가 쿠키를 저장합니다.

### 2. 이후 요청에서는 Access Token으로 사용자를 확인합니다.

사용자가 자신의 프로필을 수정한다고 가정하겠습니다.

```http
PUT /api/people/my-public-id
```

브라우저는 Access Token 쿠키를 보내고, 프론트엔드는 CSRF 쿠키 값을 읽어 헤더에도 넣습니다.

서버에서는 다음을 각각 확인합니다.

- CSRF 검사: 요청 출처와 CSRF 쿠키·헤더가 조건을 충족하는가?
- 인증 검사: 유효한 Access Token을 가진 사용자인가?
- 소유권 검사: 로그인한 사용자의 프로필인가?

모두 통과하면 Service가 프로필을 변경하고 트랜잭션을 커밋합니다.

### 3. Access Token이 만료되면 재발급을 시도합니다.

Access Token의 기본 유효기간은 15분입니다.

시간이 지났다고 서버가 브라우저에 먼저 “로그아웃하세요”라고 알려주는 것은 아닙니다. 다음 요청이 들어왔을 때 유효한 인증 정보
를 제시하지 못하면서 문제가 드러납니다.

자동 재발급 함수를 사용하는 요청은 다음 흐름으로 처리됩니다.

```text
원래 API 요청
→ 인증 실패 응답
→ POST /auth/refresh
→ 새 인증 쿠키 발급
→ 원래 API 요청 재시도
```

이때 브라우저가 Refresh Token 쿠키를 재발급 요청에 붙입니다.

서버는 DB 기록을 확인하고, 정상적인 토큰이라면 다음 작업을 수행합니다.

```text
기존 Refresh Token 폐기
→ 새 Refresh Token 저장
→ 새 Access Token 발급
→ 새 쿠키 전달
```

기존 Refresh Token 폐기와 새 Refresh Token 저장은 같은 DB 트랜잭션에서 처리합니다.

### 4. 겹치는 재발급 요청은 프론트엔드와 서버가 각각 처리합니다.

프론트엔드의 refreshInFlight는 현재 페이지에서 진행 중인 재발급 결과를 공유합니다.

```text
여러 요청에서 인증 실패
→ 진행 중인 재발급을 함께 기다림
→ 완료 후 각자 원래 요청 재시도
```

서버에서는 @Version으로 같은 DB 기록을 동시에 변경하는 충돌을 감지합니다.

이미 폐기된 Refresh Token이 다시 들어온 경우에는 재사용으로 판단해 해당 사용자의 Refresh Token 기록을 모두 삭제합니다.

### 5. 로그아웃하면 현재 Refresh Token을 폐기합니다.

```http
POST /auth/logout
```

서버는 요청에 담긴 Refresh Token의 폐기 시각을 DB에 기록하고, 브라우저 쿠키를 삭제하도록 응답합니다.

```text
현재 Refresh Token 폐기
→ Access Token 쿠키 삭제
→ Refresh Token 쿠키 삭제
→ CSRF 쿠키 삭제
```

브라우저는 이후 요청에 기존 인증 쿠키를 보내지 않게 됩니다.

일반 로그아웃은 현재 Refresh Token을 대상으로 합니다. 앞서 설명한 재사용 탐지 시 전체 Refresh Token 삭제와는 적용 범위가 다
릅니다.

### 6. 현재 구현에서 알아둘 한계

첫째, 로그아웃해도 이미 복사된 Access Token은 만료 전까지 사용할 수 있습니다.

현재 서버는 Access Token을 요청마다 DB의 폐기 목록과 비교하지 않습니다. 서명과 만료 등을 검사하므로, 쿠키를 지웠어도 외부에 복사된 JWT 자체가 무효화되는 것은 아닙니다.

```text
Refresh Token 폐기 → 이후 재발급 차단
Access Token 만료 → 해당 Access Token 사용 차단
```

둘째, 정상적인 중복 요청도 Refresh Token 재사용으로 판단될 수 있습니다.

서버는 이미 폐기된 토큰이 들어온 이유가 탈취인지, 재시도인지 확실하게 구분할 수 없습니다. 현재 정책은 사용자의 Refresh Token을 모두 삭제하므로 다른 기기에서도 재로그인이 필요해질 수 있습니다.

셋째, 자동 재발급이 모든 요청에 적용되는 것은 아닙니다.

apiFetchWithAutoRefresh()로 보낸 요청에 적용됩니다. 또 현재 코드는 403에도 재발급을 시도하지만, 소유권 부족으로 거부된 요청은 재발급해도 해결되지 않습니다.

넷째, CSRF 방어에도 추가 검토할 부분이 있습니다.

현재 로그인·회원가입·재발급·로그아웃 경로는 CSRF 필터에서 제외됩니다. 요청 출처 비교도 스킴과 포트를 엄격하게 비교하는 구현은 아닙니다. 앞서 설명한 “같은 출처 확인”은 코드의 의도이며, 현재 구현이 출처의 모든 요소를 엄격하게 검증한다고 이해하면 안됩니다.

내부 Worker API의 CSRF 제외는 별도 HMAC 인증과 연결되지만, 인증 경로의 제외 정책은 별도로 검토해야 합니다.

여기까지가 회원가입·로그인·토큰 관리 단원입니다. 다음은 2-1. 계정 User와 프로필 Person을 분리한 이유로 넘어가겠습니다.

<!-- 학습 원문 끝: lesson-22 -->

</details>

<!-- 학습 본문 끝 -->

---

<details>
<summary>원문 표현의 기술적 정정·검토 메모</summary>

## 원문과 분리한 검토 메모

아래 내용은 원문을 바꾼 것이 아니라, 학습 시 혼동을 줄이기 위해 별도로 남긴 확인 사항입니다.

- CSRF 헤더의 실제 이름은 `X-CSRF-TOKEN`입니다. 쿠키 보안 옵션 부분에 있는 `X-XSRF-TOKEN` 표기는 원문 그대로 남겼습니다.
- `Secure=true`는 HTTPS 운영의 권장 설정입니다. 확인 당시 `application-prod.yml`의 세 쿠키 Secure 값은 테스트 용도로 false였습니다. 원문의 표에 있는 “운영 환경 true”와 실제 적용 여부를 구분해야 합니다.
- 값 객체가 앞뒤 공백을 제거하더라도 HTTP 요청은 그 전에 DTO의 이메일·정규식 검증에서 거부될 수 있습니다.
- 현재 User는 IDENTITY ID 생성을 사용하므로 `save()`에서도 INSERT가 즉시 실행될 수 있습니다. `saveAndFlush()`의 의미는 현재 위치에서 flush까지 수행한다는 것이며, `save()`가 항상 SQL을 미룬다는 뜻은 아닙니다.
- 외부 사이트가 임의의 DELETE 요청을 제한 없이 보낼 수 있는 것은 아닙니다. 실제 CSRF 요청의 성립 여부는 SameSite·CORS·HTTP 메서드 등 브라우저 조건과 서버 정책에 따라 달라집니다. 단순 CSRF 쿠키·헤더 일치가 요청 출처를 절대적으로 증명하지도 않습니다.
- 인증 API의 CSRF 제외는 현재 구현이며, 로그인 전에 쿠키가 없다는 이유만으로 refresh·logout까지 모두 안전하다고 판단할 수는 없습니다. 현재 출처 비교는 스킴·포트를 엄격히 검증하지 않습니다.
- 동시 재발급은 같은 version을 읽은 경우 낙관적 락 충돌로 처리되지만, 후속 요청이 폐기 후 조회하면 재사용 탐지로 전체 Refresh Token을 삭제할 수 있습니다. 기존 Access Token은 그 순간 자동 폐기되지 않습니다.
- 일반 Entity 변경에서는 JPA가 version을 증가시키지만, 로그아웃의 일괄 UPDATE는 version 증가를 직접 쿼리에 명시합니다.

관련 코드와 더 자세한 검토 내용은 [요약·검토본](./01-auth-signup-login-token-summary.md)을 참고합니다.

</details>
