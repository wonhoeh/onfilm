# Onfilm DTO 스타일 컨벤션

## 1. 기본 원칙

Onfilm의 DTO는 `record`를 기본으로 사용한다.

DTO는 계층 또는 프로세스 사이에서 데이터를 전달하는 불변 값 객체다. 생성 이후 값을 변경할 이유가 없다면 class와 Lombok으로 getter, 생성자, `equals`, `hashCode`를 만드는 대신 record로 의도를 명확하게 표현한다.

```text
Request DTO          → record
Response DTO         → record
Kafka Message        → record
Application Event    → record
단순 조회 Projection → record
JPA Entity           → class
```

관련 문서:

- [Onfilm 검증 흐름 컨벤션](validation-flow-convention.md)
- [Onfilm 엔티티 설계·리팩토링 가이드](entity-refactoring-style-guide.md)

---

## 2. 기본 DTO 형태

### 2.1 Request DTO

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

- Bean Validation annotation은 record component에 선언한다.
- Controller의 `@RequestBody`에는 `@Valid`를 붙인다.
- DTO 생성자에 Entity의 도메인 불변식을 복사하지 않는다.
- 입력값을 조용히 기본값으로 바꾸지 않는다.

### 2.2 Response DTO

```java
public record StoryboardProjectResponse(
        Long id,
        String title
) {
    public static StoryboardProjectResponse from(StoryboardProject project) {
        return new StoryboardProjectResponse(
                project.getId(),
                project.getTitle()
        );
    }
}
```

- Entity 자체를 API 응답으로 반환하지 않는다.
- 단순 변환은 `from()` 또는 `of()` 정적 팩토리로 표현할 수 있다.
- 변환 과정에서 Repository 조회, 권한 검사, 상태 변경을 수행하지 않는다.

### 2.3 Message와 Event

```java
public record MediaEncodeRequestedMessage(
        int schemaVersion,
        String jobId,
        String requestId,
        Long movieId,
        Instant requestedAt
) {
}
```

```java
public record StorageFilesDeleteEvent(List<String> keys) {
    public StorageFilesDeleteEvent {
        if (keys == null) {
            throw new IllegalArgumentException("keys is required");
        }
        keys = List.copyOf(keys);
    }
}
```

- 프로세스 간 메시지는 schema version과 필드 호환성 정책을 명시한다.
- Event는 발행 이후 값이 바뀌지 않도록 컬렉션을 방어적으로 복사한다.
- token, password, secret 같은 민감정보는 Message와 Event에 불필요하게 포함하지 않는다.

---

## 3. class DTO를 허용하는 예외

다음 중 하나가 실제로 필요할 때만 class DTO를 사용한다.

### 3.1 프레임워크가 기본 생성자와 setter를 요구함

레거시 라이브러리 또는 특정 바인딩 도구가 mutable JavaBean을 요구하는 경우다.

```java
@Getter
@Setter
@NoArgsConstructor
public class LegacyRequest {
    private String value;
}
```

class를 사용한 이유를 주석이나 설계 문서로 남긴다. Spring Boot 3와 Jackson의 일반적인 JSON Request/Response에는 record를 사용할 수 있다.

### 3.2 값이 단계적으로 조립되어야 함

여러 단계에서 값을 추가하는 가변 검색 조건이나 외부 라이브러리용 객체처럼 불변 생성이 부자연스러운 경우다.

가능하면 builder보다 생성자, 정적 팩토리, 작은 DTO 조합으로 불변 구조를 먼저 검토한다.

### 3.3 DTO 상속이 반드시 필요함

record는 다른 클래스를 상속할 수 없다. 하지만 DTO 상속은 JSON 타입과 필드 관계를 복잡하게 하므로 가능한 한 composition을 사용한다.

```java
public record PageResponse<T>(
        List<T> content,
        PageInfo page
) {
}
```

단순히 기존 DTO가 class라는 이유는 예외 사유가 아니다.

---

## 4. Request DTO 검증 스타일

### 4.1 annotation 선택

| annotation | 사용 기준 |
|---|---|
| `@NotNull` | 객체, 숫자, enum, 컬렉션 자체가 필수 |
| `@NotBlank` | 필수 문자열이 null·빈 값·공백이면 안 됨 |
| `@Size` | 문자열과 컬렉션의 길이 제한 |
| `@Positive` | ID나 양수 값 |
| `@PositiveOrZero` | 0을 포함하는 수량과 순서 |
| `@Email`, `@Pattern` | 요청 형식 |
| `@Valid` | 중첩 DTO 검증 전파 |

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

컬렉션 자체, 각 원소, 중첩 DTO는 서로 다른 검증 대상이다.

### 4.2 필드 사이 관계는 class-level constraint

비밀번호와 비밀번호 확인처럼 여러 component를 함께 검사해야 하면 custom Bean Validation constraint를 사용한다.

```java
@PasswordConfirmed
public record SignupRequest(
        @NotBlank String password,
        @NotBlank String passwordConfirm
) {
}
```

compact constructor에서 직접 예외를 던지면 Jackson 역직렬화 오류와 Bean Validation 오류의 응답 형태가 달라질 수 있으므로 API 요청 검증에는 사용하지 않는다.

### 4.3 DTO에서 하지 않을 일

- Repository 조회
- 사용자 권한·소유권 검증
- Entity 상태 변경
- 외부 API 또는 Storage 호출
- 트랜잭션 처리
- Entity 불변식의 중복 구현
- 잘못된 값을 임의의 기본값으로 치환

```java
// 지양: 누락된 요청을 성공 가능한 값으로 바꾼다.
public ExampleRequest {
    items = items == null ? List.of() : items;
}
```

선택 입력의 null을 빈 목록으로 취급하는 것이 명시된 API 계약일 때만 변환한다.

---

## 5. 컬렉션과 불변성

record 자체가 불변이라는 것은 component가 참조하는 컬렉션까지 불변이라는 뜻은 아니다.

Response, Message, Event처럼 생성 이후 내용이 바뀌면 안 되는 DTO는 방어적으로 복사한다.

```java
public record GalleryResponse(List<GalleryItemResponse> items) {
    public GalleryResponse {
        items = List.copyOf(items);
    }
}
```

Request DTO에서 `@NotNull` 위반을 Bean Validation으로 처리하려면 null을 먼저 빈 컬렉션으로 바꾸지 않는다.

```java
public record GalleryRequest(
        @NotNull List<@NotNull String> keys
) {
    public GalleryRequest {
        if (keys != null) {
            keys = List.copyOf(keys);
        }
    }
}
```

Map과 Set도 각각 `Map.copyOf()`, `Set.copyOf()`를 사용한다. 내부 원소가 mutable이면 원소 자체의 불변성도 별도로 검토한다.

---

## 6. 변환 메서드 규칙

### 6.1 `from`

하나의 원본 객체에서 DTO를 만들 때 사용한다.

```java
public static MovieResponse from(Movie movie) {
    return new MovieResponse(movie.getId(), movie.getTitle());
}
```

### 6.2 `of`

여러 값이나 여러 원본을 조합해 DTO를 만들 때 사용할 수 있다.

```java
public static ProfileResponse of(Person person, boolean editable) {
    return new ProfileResponse(person.getPublicId(), person.getName(), editable);
}
```

### 6.3 Mapper 또는 Service에서 변환

다음 상황에서는 DTO 정적 팩토리보다 별도 Mapper나 Query Service가 적합하다.

- 여러 Repository 조회 결과를 조합
- 권한에 따라 응답 필드가 달라짐
- 외부 URL 생성 같은 인프라 의존성이 필요
- Entity graph가 크고 LAZY loading 전략을 통제해야 함
- 같은 Entity를 여러 API 표현으로 변환

DTO의 `from()`이 Entity 탐색을 반복해 N+1을 숨기지 않도록 주의한다.

---

## 7. Value Object와 DTO 구분

DTO와 Value Object는 둘 다 불변일 수 있지만 책임이 다르다.

| 구분 | DTO | Value Object |
|---|---|---|
| 목적 | 계층·프로세스 사이 데이터 전달 | 도메인 값과 규칙 표현 |
| 검증 | API 형식 | 도메인 불변식 |
| 수명 | 요청·응답·메시지 처리 동안 | Entity 상태의 일부 또는 도메인 연산 동안 |
| 예 | `SignupRequest` | `UserEmail`, `Username`, `GenreName` |

단순한 값 묶음은 record Value Object로 만들 수 있다. 생성 통제, 복잡한 정규화, 제한적인 공개 API가 필요하면 private 생성자를 가진 final class가 적합하다.

모든 final class를 record로 바꾸거나 모든 record를 DTO라고 부르지 않는다.

---

## 8. 네이밍과 패키지

용도를 이름에 명확하게 드러낸다.

```text
CreateMovieRequest
MovieCardResponse
MediaEncodeRequestedMessage
StorageFilesDeleteEvent
```

- 입력: `*Request`
- 출력: `*Response`
- 비동기 메시지: `*Message`
- 애플리케이션 이벤트: `*Event`
- 단순히 `Dto`, `Data`, `Info`로 끝나는 모호한 이름은 피한다.

기능별 패키지 안에서 `dto`, `message`, `event`처럼 역할에 따라 배치한다. Request와 Response 수가 많아지면 하위 패키지 분리를 검토한다.

---

## 9. 직렬화 계약

class DTO를 record로 바꿔도 JSON 필드명과 형식은 유지해야 한다.

변환 전에 확인할 항목:

- `@JsonProperty`로 변경한 필드명
- `@JsonFormat` 날짜 형식
- null 필드 포함 정책
- boolean 필드명과 JSON 이름
- enum 문자열 값
- 역직렬화에 사용하던 custom constructor
- 알 수 없는 필드 허용 정책
- Kafka Message의 schema version과 하위 호환성

Java 호출부는 다음처럼 변경된다.

```java
request.getTitle(); // class
request.title();    // record
```

JSON의 `title` 필드명은 그대로 유지된다.

---

## 10. class에서 record로 전환하는 절차

1. setter 또는 필드 변경 사용처를 찾는다.
2. 기본 생성자에 의존하는 프레임워크가 있는지 확인한다.
3. Lombok builder와 생성자 사용처를 찾는다.
4. component 순서와 타입을 정한다.
5. Bean Validation과 Jackson annotation을 component로 옮긴다.
6. mutable 컬렉션의 방어적 복사를 결정한다.
7. `get*()` 호출을 record accessor로 변경한다.
8. JSON 요청·응답 계약 테스트를 실행한다.
9. QueryDSL projection과 template binding을 확인한다.
10. 사용하지 않는 Lombok annotation과 import를 제거한다.

한 번에 모든 DTO를 기계적으로 바꾸기보다 기능 단위로 변환하고 Controller·Service·테스트를 함께 수정한다.

---

## 11. 테스트 기준

Request DTO:

- 필수값 null·blank
- 문자열과 컬렉션 경계 길이
- 숫자 범위
- 중첩 DTO와 컬렉션 원소 검증
- class-level 복합 constraint
- JSON 역직렬화

Response DTO:

- Entity 또는 projection 변환 결과
- JSON 필드명과 날짜 형식
- nullable 필드 정책
- 컬렉션 불변성

Message와 Event:

- JSON round-trip
- schema version
- 필수 식별자와 시간
- 컬렉션 방어적 복사
- 민감정보가 포함되지 않는지

---

## 12. 리뷰 체크리스트

- [ ] DTO가 특별한 이유 없이 class로 작성되지 않았는가?
- [ ] class DTO라면 mutable이어야 하는 이유가 명확한가?
- [ ] Request component에 적절한 Bean Validation이 있는가?
- [ ] Controller에 `@Valid`가 있는가?
- [ ] 중첩 DTO와 컬렉션 원소도 검증하는가?
- [ ] DTO가 Entity 불변식이나 Service 로직을 구현하지 않는가?
- [ ] Response에서 Entity를 직접 노출하지 않는가?
- [ ] 컬렉션이 필요한 수준으로 방어적 복사되는가?
- [ ] 변환 메서드가 Repository나 외부 시스템에 의존하지 않는가?
- [ ] JSON 필드명과 날짜·enum 형식이 기존 계약과 같은가?
- [ ] Message schema 하위 호환성을 검토했는가?
- [ ] DTO 변환 테스트가 있는가?

---

## 13. 최종 규칙

```text
DTO는 record가 기본값이다.
class DTO는 mutable 바인딩, 상속, 레거시 프레임워크 요구가 있을 때만 사용한다.
DTO는 요청 형식과 데이터 전달을 담당하고 도메인 불변식과 유스케이스를 구현하지 않는다.
```
