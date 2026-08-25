# Onfilm 검증 흐름 컨벤션

## 1. 목적

Onfilm의 입력값은 DTO, Service, Entity·Value Object, DB를 거치며 검증된다. 여러 계층에서 검증한다는 이유로 같은 `null`, blank, 길이 검사를 복사하지 않는다. 각 계층은 자신만 판단할 수 있는 내용을 검증하고 다음 계층은 독립적인 최종 방어선 역할을 한다.

기본 흐름:

```text
HTTP Request
    ↓
DTO: 요청 형식과 사용자 입력 오류
    ↓
Service: 권한, 존재 여부, 소유권, 외부 상태
    ↓
Entity / Value Object: 항상 지켜야 하는 도메인 불변식
    ↓
DB: 동시성까지 포함한 최종 데이터 무결성
```

관련 문서:

- [Domain Validation 위치 결정 가이드](../review/validation/domain-validation-location-guide.md)
- [Onfilm DTO 스타일 컨벤션](dto-style-convention.md)
- [Onfilm 엔티티 설계·리팩토링 가이드](entity-refactoring-style-guide.md)
- [Entity 메서드 네이밍 컨벤션](entity-method-naming-convention.md)
- [Onfilm 예외 정책과 오류 코드 컨벤션](exception-and-error-code-convention.md)

---

## 2. 계층별 책임 요약

| 계층 | 질문 | 주요 검증 |
|---|---|---|
| DTO | 요청 형식이 API 계약에 맞는가? | null, blank, 길이, 숫자 범위, 컬렉션 크기 |
| Service | 현재 사용자가 이 유스케이스를 수행할 수 있는가? | 권한, 존재 여부, 소유권, 외부 파일, 사전 중복 조회 |
| Entity / Value Object | 이 객체가 어떤 호출 경로에서도 유효한가? | 생성·변경 불변식, 정규화, 상태 전이, 연관관계 |
| DB | 경쟁 조건에서도 데이터가 유효한가? | NOT NULL, FK, UNIQUE, CHECK, optimistic lock |

일부 검증이 결과적으로 겹치는 것은 정상이다. 단, 목적과 실패 시점이 달라야 한다.

---

## 3. DTO 검증

DTO는 외부 요청의 형식을 검증하고 클라이언트가 이해할 수 있는 메시지를 제공한다.

```java
public record StoryboardProjectRequest(
        @NotBlank(message = "스토리보드 제목은 필수입니다.")
        @Size(
                max = StoryboardProject.TITLE_MAX_LENGTH,
                message = "스토리보드 제목은 120자 이하여야 합니다."
        )
        String title
) {
}
```

컨트롤러는 `@Valid`를 빠뜨리지 않는다.

```java
@PostMapping
public ResponseEntity<?> create(
        @Valid @RequestBody StoryboardProjectRequest request
) {
    return ResponseEntity.ok(service.createProject(request));
}
```

### 3.1 DTO가 담당하는 검증

- `@NotNull`: 값 자체가 반드시 필요
- `@NotBlank`: 문자열이 null, 빈 문자열, 공백 문자열이면 안 됨
- `@Size`: 문자열·컬렉션 길이
- `@Positive`, `@PositiveOrZero`, `@Min`, `@Max`: 숫자 범위
- `@Email`, `@Pattern`: 요청 형식
- 중첩 DTO의 `@Valid`
- 컬렉션 자체와 컬렉션 원소 검증

```java
public record StoryboardSceneOrderRequest(
        @NotNull(message = "sceneIds는 필수입니다.")
        List<@NotNull @Positive Long> sceneIds
) {
}
```

```java
public record ParentRequest(
        @NotNull
        List<@NotNull @Valid ChildRequest> children
) {
}
```

### 3.2 DTO 검증만으로 충분하지 않은 이유

Entity와 Service는 다음 경로에서도 호출될 수 있다.

- 다른 Service
- 이벤트 리스너
- 스케줄러와 배치
- 테스트와 데이터 초기화
- 메시지 Consumer

따라서 DTO 검증은 도메인 불변식을 대체하지 않는다.

---

## 4. Service 검증

Service는 Repository, 현재 사용자, 외부 시스템을 알아야 판단할 수 있는 유스케이스 조건을 검증한다.

```java
@Transactional
public StoryboardProject updateProject(
        Long projectId,
        StoryboardProjectRequest request
) {
    StoryboardProjectRequest requiredRequest = require(request, "request");
    Person person = findCurrentPerson();
    StoryboardProject project = findProject(person, projectId);

    project.changeTitle(requiredRequest.title());
    return project;
}
```

### 4.1 Service가 담당하는 검증

- 로그인 사용자와 권한
- Entity 존재 여부
- 현재 사용자가 해당 Aggregate의 소유자인지
- 다른 Aggregate와 연결할 수 있는지
- storage key가 사용자·Aggregate에게 속하는지
- 외부 파일이나 자원이 실제로 존재하는지
- Repository를 이용한 빠른 중복 확인
- 유스케이스에 필요한 트랜잭션과 lock 정책

```java
storageKeyPolicy.validateStoryboardCardKey(personId, imageKey);
```

```java
if (!storageService.exists(sourceKey)) {
    throw new IllegalArgumentException("source file does not exist");
}
```

### 4.2 Service에서 반복하지 않을 검증

Controller의 `@Valid`와 Entity가 이미 담당하는 단순 필드 검증을 Service에 다시 복사하지 않는다.

```java
// 지양
if (request.title() == null || request.title().isBlank()) {
    throw new IllegalArgumentException("title is required");
}
if (request.title().length() > 120) {
    throw new IllegalArgumentException("title is too long");
}
```

```java
// 권장
project.changeTitle(request.title());
```

Service가 Controller 외부에서도 호출될 수 있다면 request 객체 자체의 null은 방어적으로 확인할 수 있다.

```java
StoryboardProjectRequest requiredRequest = require(request, "request");
```

이 검사는 DTO 필드 검증을 다시 구현하는 것이 아니라 Service public API의 계약을 보호한다.

### 4.3 `@Validated`를 사용할 때

Service public 메서드의 파라미터 계약을 Bean Validation으로 선언할 수 있다.

```java
@Validated
@Service
public class ExampleService {
    public void execute(@NotNull @Positive Long id) {
        // ...
    }
}
```

다만 Spring proxy를 거쳐 호출될 때 적용되므로 같은 클래스 내부의 self-invocation에는 기대하지 않는다. 핵심 도메인 불변식은 여전히 Entity가 검증한다.

---

## 5. Entity와 Value Object 검증

Entity는 생성되는 순간부터 유효해야 하며 모든 상태 변경 이후에도 불변식을 지켜야 한다.

```java
private StoryboardProject(String title) {
    this.title = requireTitle(title);
}

public void changeTitle(String title) {
    this.title = requireTitle(title);
}

private static String requireTitle(String title) {
    if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("title is required");
    }

    String trimmed = title.trim();
    if (trimmed.length() > TITLE_MAX_LENGTH) {
        throw new IllegalArgumentException("title is too long");
    }
    return trimmed;
}
```

### 5.1 Entity가 담당하는 검증

- 생성 시 필수 값
- 생성과 변경에 공통인 길이와 범위
- 필드 정규화
- 필드 사이의 조건
- 허용된 상태 전이
- 부모·자식 연관관계 일관성
- 다른 부모로 재할당 방지
- 자식 컬렉션의 중복과 최대 개수
- 재정렬 요청이 기존 원소의 정확한 순열인지

```java
if (role == PersonRole.ACTOR) {
    this.castType = require(castType, "castType");
    this.characterName = normalizeCharacterName(characterName);
} else {
    this.castType = null;
    this.characterName = null;
}
```

역할에 따라 다른 필드가 필요하다는 것은 HTTP 요청 형식이 아니라 `MoviePerson` 자체의 불변식이다.

### 5.2 Value Object로 분리할 기준

다음 조건 중 여러 개에 해당하면 검증·정규화를 Value Object로 분리한다.

- 여러 Entity와 Service에서 반복 사용
- 정규화 값과 표시 값이 모두 필요
- 검증 규칙이 길고 복잡함
- 그 값 자체에 분명한 도메인 의미가 있음

현재 예:

- `UserEmail`
- `Username`
- `GenreName`

`TokenHashing`처럼 상태를 가지지 않고 변환만 담당하면 Value Object가 아니라 검증·변환 전용 유틸리티 또는 정책 객체로 구분한다.

```java
Username username = Username.from(rawUsername);
```

Value Object를 사용하면 문자열 검증을 호출 계층마다 복사하지 않아도 된다.

---

## 6. JPA 컬럼과 DB 검증

`@Column`은 Java 객체를 생성할 때 값을 검증하는 기능이 아니다.

```java
@Column(nullable = false, length = TITLE_MAX_LENGTH)
private String title;
```

`nullable = false`는 주로 DDL의 `NOT NULL`과 ORM 매핑 의도를 표현한다. 잘못된 값은 flush나 commit 시점까지 발견되지 않을 수 있으므로 Entity 검증이 별도로 필요하다.

### 6.1 DB가 담당하는 검증

- `NOT NULL`
- FK
- 단일·복합 `UNIQUE`
- 필요한 `CHECK` constraint
- optimistic locking을 위한 `@Version`
- 조회와 제약 확인에 필요한 index

```java
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_email",
                columnNames = "email"
        )
)
```

### 6.2 중복은 Service 검사와 DB 제약을 함께 둔다

Service 사전 검사는 빠르고 친절한 오류를 제공한다.

```java
if (userRepository.existsByEmail(email)) {
    throw new DuplicateEmailException();
}
```

하지만 동시에 들어온 두 트랜잭션이 모두 “중복 없음”을 확인할 수 있다. 최종적으로는 DB unique constraint가 경쟁 조건을 막아야 한다.

```text
Service exists 검사: 빠른 실패와 명확한 오류
DB UNIQUE: 동시 요청에서도 최종 무결성 보장
```

DB unique 위반은 `DataIntegrityViolationException` 등을 API의 의미 있는 중복 오류로 변환한다.

---

## 7. 검증과 정규화 구분

검증은 허용할 수 없는 값을 거부하고, 정규화는 허용된 값을 일관된 형태로 변환한다.

```text
검증: null 제목 → 예외
검증: 121자 제목 → 예외
정규화: "  제목  " → "제목"
정규화: 선택 입력 "   " → null
정규화: "Action" → 중복 비교용 "action"
```

정규화한 값을 실제로 저장한다면 생성과 변경에서 동일한 함수를 사용한다.

```java
private PersonSns(SnsType type, String url) {
    this.type = requireType(type);
    this.url = normalizeUrl(url);
}
```

외부 입력 원문이 필요하면 표시 값과 정규화 값을 분리해서 저장한다.

```java
private String username;
private String usernameNormalized;
```

---

## 8. 검증 상수 관리

같은 제한값을 여러 계층에 숫자로 반복하지 않는다.

```java
public static final int TITLE_MAX_LENGTH = 120;

@Column(nullable = false, length = TITLE_MAX_LENGTH)
private String title;
```

```java
public record StoryboardProjectRequest(
        @NotBlank
        @Size(max = StoryboardProject.TITLE_MAX_LENGTH)
        String title
) {
}
```

- DTO가 참조해야 하면 `public static final`
- Entity 내부에서만 사용하면 `private static final`
- Value Object가 값의 정책을 소유하면 그 Value Object의 상수를 참조
- DB migration의 길이도 같은 정책과 일치시킴

상수가 순환 의존이나 계층 역전을 만든다면 공용 정책 클래스 또는 Value Object로 이동한다.

---

## 9. 복합 검증을 둘 위치

### 9.1 한 요청 안의 형식 관계

비밀번호 확인처럼 요청에서만 존재하는 필드 관계는 class-level custom Bean Validation constraint를 사용한다.

```java
@PasswordConfirmed
public record SignupRequest(
        @NotBlank String password,
        @NotBlank String passwordConfirm
) {
}
```

custom `ConstraintValidator`는 두 필드가 모두 입력된 경우의 관계만 검사하고, 각 필드의 null·blank 검증은 `@NotBlank`에 맡긴다. DTO compact constructor에서 직접 예외를 던지면 Jackson 역직렬화 오류와 Bean Validation 오류의 응답 형식이 달라질 수 있으므로 API 요청 검증에는 사용하지 않는다.

### 9.2 Entity 상태만으로 판단 가능한 관계

역할과 배역 정보, 상태 전이, 시작·완료 시각 선후 관계는 Entity가 검증한다.

### 9.3 Repository나 외부 시스템이 필요한 관계

이메일 중복, 리소스 소유권, storage 파일 존재 여부는 Service가 검증한다.

### 9.4 동시성에서만 최종 판단 가능한 관계

동일 이메일·username·복합 business key 중복은 DB 제약을 최종 기준으로 둔다.

---

## 10. 대표 검증 흐름

### 10.1 스토리보드 제목 수정

```text
DTO
  @NotBlank, @Size
    ↓
Service
  현재 사용자, 프로젝트 존재, 소유권 확인
    ↓
Entity
  changeTitle()에서 trim과 불변식 검증
    ↓
DB
  NOT NULL, column length
```

### 10.2 회원가입 이메일

```text
DTO
  @NotBlank, @Email, @Size
    ↓
Value Object
  이메일 정규화와 도메인 형식 검증
    ↓
Service
  existsByEmail() 사전 확인
    ↓
Entity
  유효한 UserEmail만 받아 생성
    ↓
DB
  UNIQUE로 동시 요청 경쟁 조건 차단
```

### 10.3 스토리보드 카드 storage key

```text
DTO
  @Size, 필수 여부
    ↓
Service / StorageKeyPolicy
  경로 형식, 현재 Person 소유 prefix 확인
    ↓
Entity
  선택값 정규화와 최대 길이 보장
    ↓
DB
  column length, FK
```

### 10.4 상태 머신 변경

```text
DTO
  상태 변경에 필요한 값과 시간 형식
    ↓
Service
  작업 존재·권한 확인, Clock으로 현재 시각 생성
    ↓
Entity
  현재 상태에서 전이 가능한지와 시간 선후 관계 확인
    ↓
DB
  NOT NULL, @Version으로 동시 변경 감지
```

---

## 11. 예외 처리 스타일

API에 공개되는 예외 타입, 오류 코드, HTTP 상태와 응답 형식의 상세 기준은 [Onfilm 예외 정책과 오류 코드 컨벤션](exception-and-error-code-convention.md)을 따른다.

| 상황 | 기본 예외 |
|---|---|
| 잘못된 인자·형식 | `IllegalArgumentException` |
| 현재 상태에서 수행 불가 | `IllegalStateException` |
| 조회 실패 | `*NotFoundException` |
| API에서 별도로 표현할 도메인 오류 | custom exception |
| DB 중복 경쟁 조건 | persistence exception을 도메인 중복 예외로 변환 |

Entity 예외 메시지는 개발자가 불변식 위반 원인을 알 수 있게 작성한다. DTO의 validation message는 클라이언트가 이해할 수 있게 작성한다.

민감한 값은 예외와 로그에 포함하지 않는다.

```java
// 지양
throw new IllegalArgumentException("invalid token: " + rawToken);

// 권장
throw new InvalidRefreshTokenException();
```

---

## 12. 피해야 할 패턴

### 12.1 Service에 DTO 검증 복사

```java
if (request.name() == null) ...
if (request.name().isBlank()) ...
if (request.name().length() > 60) ...
```

DTO와 Entity가 담당할 단순 필드 검증을 Service에 반복하지 않는다.

### 12.2 DTO만 믿고 Entity 검증 생략

```java
private StoryboardProject(String title) {
    this.title = title;
}
```

HTTP 밖의 호출 경로에서 잘못된 엔티티가 만들어질 수 있다.

### 12.3 `@Column(nullable = false)`를 Java 검증으로 오해

DB 오류가 flush 시점까지 늦어지고 도메인과 무관한 persistence 예외가 발생한다.

### 12.4 사전 중복 조회만 사용

```java
if (!repository.existsByEmail(email)) {
    repository.save(user);
}
```

동시 요청 경쟁 조건을 막지 못하므로 DB unique constraint가 반드시 필요하다.

### 12.5 Controller에서 도메인 규칙 구현

Controller는 요청 변환과 응답에 집중한다. 역할별 필드 규칙, 상태 전이, 소유권 판단을 Controller에 두지 않는다.

---

## 13. 리뷰 체크리스트

### DTO

- [ ] Controller 파라미터에 `@Valid`가 있는가?
- [ ] `@NotNull`, `@NotBlank`, `@Size`를 의미에 맞게 사용했는가?
- [ ] 중첩 DTO와 컬렉션 원소까지 검증하는가?
- [ ] validation message가 사용자 관점에서 이해 가능한가?

### Service

- [ ] 단순 null·blank·길이 검사를 복사하지 않았는가?
- [ ] 권한, 존재, 소유권, 외부 상태를 검증하는가?
- [ ] 사전 중복 조회와 DB unique 위반을 모두 처리하는가?
- [ ] request 객체 자체의 null 정책이 명확한가?

### Entity / Value Object

- [ ] 생성과 모든 변경 경로에서 불변식을 지키는가?
- [ ] 검증과 정규화가 한곳에 모여 있는가?
- [ ] 복잡하고 재사용되는 값 규칙을 Value Object로 분리했는가?
- [ ] 상태 전이와 연관관계 규칙이 Entity 안에 있는가?

### DB

- [ ] 필수 컬럼에 NOT NULL이 있는가?
- [ ] 중복 불변식에 UNIQUE가 있는가?
- [ ] FK와 index가 조회·무결성 정책에 맞는가?
- [ ] 동시 상태 변경이 중요하면 `@Version`이 있는가?

### 테스트

- [ ] DTO validation 테스트가 있는가?
- [ ] Entity 경계값과 불변식 테스트가 있는가?
- [ ] DB unique·FK·version 영속성 테스트가 있는가?
- [ ] Service 권한·소유권·외부 실패 테스트가 있는가?
- [ ] 동시 요청 경쟁 조건 또는 DB 위반 변환을 검증하는가?
