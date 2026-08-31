# Onfilm 엔티티 설계·리팩토링 가이드

## 1. 목적과 적용 범위

이 문서는 Onfilm에서 엔티티를 리팩토링하며 정리한 설계 스타일을 새 엔티티에도 일관되게 적용하기 위한 기준이다.

주요 참고 구현:

- Aggregate Root: `Person`, `Movie`, `StoryboardProject`, `StoryboardScene`, `User`
- 자식·연결 엔티티: `PersonSns`, `ProfileTag`, `MoviePerson`, `MoviePersonRole`, `MovieGenre`, `StoryboardCard`, `Trailer`
- 상태 머신 엔티티: `RefreshToken`, `MediaEncodeJob`, `MediaEncodeOutbox`, `MediaUploadRequest`
- 트랜잭션 이후 외부 작업: `StorageFilesDeleteEvent`, `StorageFilesDeleteEventListener`

메서드 이름은 [Entity 메서드 네이밍 컨벤션](entity-method-naming-convention.md)을 함께 따른다.

이 가이드의 핵심은 다음 네 문장으로 요약된다.

1. 엔티티는 생성되는 순간부터 유효해야 한다.
2. 상태 변경은 setter가 아니라 의미 있는 도메인 메서드로만 수행한다.
3. 부모가 자식의 생성과 컬렉션 변경을 책임진다.
4. DB·DTO·서비스 검증은 엔티티 불변식을 보완하며 서로 대체하지 않는다.

---

## 2. 기본 클래스 구조

### 2.1 JPA 기본 생성자는 `protected`

JPA가 사용할 기본 생성자는 열어 두되 애플리케이션 코드가 불완전한 엔티티를 만들지 못하게 한다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Example {
    // ...
}
```

`public` 기본 생성자와 Lombok `@AllArgsConstructor`는 사용하지 않는다.

### 2.2 비즈니스 생성자는 `private`

필드 대입과 검증은 private 생성자에서 시작한다.

```java
private Example(String name) {
    this.name = requireName(name);
}
```

### 2.3 정적 팩토리로 생성 의도를 표현한다

일반 생성은 `create`, 상태가 의미를 가지면 도메인 동사를 사용한다.

```java
public static Genre create(String name) { ... }
public static RefreshToken issue(...) { ... }
public static MediaEncodeJob requested(...) { ... }
public static MediaEncodeOutbox pending(...) { ... }
```

- 외부에서 직접 생성하는 Aggregate Root의 팩토리는 `public static`으로 둔다.
- 부모를 통해서만 생성해야 하는 자식 팩토리는 package-private으로 둔다.
- 팩토리 이름은 최초 상태나 생성 의도를 드러낸다.

### 2.4 생성과 변경이 같은 규칙을 사용하면 `apply*`로 공통화한다

```java
private Movie(...) {
    applyBasicInfo(title, runtime, releaseYear, ageRating);
}

public void changeBasicInfo(...) {
    applyBasicInfo(title, runtime, releaseYear, ageRating);
}

private void applyBasicInfo(...) {
    this.title = requireText(title, "title");
    this.runtime = validateRuntime(runtime);
    this.releaseYear = validateReleaseYear(releaseYear);
    this.ageRating = require(ageRating, "ageRating");
}
```

공통 로직이 없는 단순 변경까지 기계적으로 `apply*`로 분리하지 않는다.

---

## 3. Aggregate Root가 자식 생성을 책임진다

외부 서비스가 자식을 직접 생성하고 부모 컬렉션을 수정하지 않는다. 부모의 공개 메서드가 자식 생성, 중복 검증, 양방향 연결을 한 번에 끝낸다.

```java
public Trailer addTrailer(String storageKey) {
    Trailer trailer = Trailer.create(storageKey);
    addTrailer(trailer);
    return trailer;
}

void addTrailer(Trailer trailer) {
    Trailer requiredTrailer = require(trailer, "trailer");
    if (hasTrailer(requiredTrailer.getStorageKey())) {
        throw new IllegalArgumentException("duplicate trailer");
    }

    requiredTrailer.attachMovie(this);
    trailers.add(requiredTrailer);
}
```

서비스 호출 코드는 다음 형태가 된다.

```java
Trailer trailer = movie.addTrailer(storageKey);
```

다음 코드는 사용하지 않는다.

```java
Trailer trailer = Trailer.create(storageKey);
trailer.attachMovie(movie);
movie.getTrailers().add(trailer);
```

이 구조의 장점:

- 자식이 부모 없이 생성되는 경로를 줄인다.
- 중복과 최대 개수 같은 컬렉션 규칙을 한곳에서 관리한다.
- 서비스가 JPA 양방향 연관관계의 구현 세부사항을 알 필요가 없다.
- 테스트가 Aggregate Root의 공개 API를 기준으로 작성된다.

### 3.1 부모가 다른 Aggregate인 경우

`User.createPerson()`, `Person.addStoryboardProject()`, `Movie.addMoviePerson()`처럼 소유권이 명확한 경우 부모가 생성한다.

반대로 여러 Aggregate가 공유하는 `Genre`, 기존 `Person`처럼 독립적인 엔티티는 자식이 새로 생성하지 않고 인자로 받는다. 공유 엔티티에 cascade를 걸어 함께 생성·삭제하지 않는다.

---

## 4. 양방향 연관관계 규칙

### 4.1 부모는 `add` / `remove`, 자식은 `attach` / `detach`

```java
// Parent
public void removeTrailer(Trailer trailer) {
    Trailer requiredTrailer = require(trailer, "trailer");
    if (!trailers.remove(requiredTrailer)) {
        throw new IllegalArgumentException("trailer does not belong to movie");
    }
    requiredTrailer.detachMovie(this);
}

// Child
void attachMovie(Movie movie) {
    Movie requiredMovie = require(movie, "movie");
    if (this.movie != null && this.movie != requiredMovie) {
        throw new IllegalStateException("trailer already belongs to another movie");
    }
    this.movie = requiredMovie;
}

void detachMovie(Movie movie) {
    if (this.movie == movie) {
        this.movie = null;
    }
}
```

규칙:

- `attach*`와 `detach*`는 같은 Aggregate 패키지 안에서만 쓰도록 package-private을 우선한다.
- 다른 부모가 이미 연결돼 있으면 재할당하지 않고 예외를 발생시킨다.
- `detach*`는 전달받은 부모와 현재 부모가 동일할 때만 해제한다.
- 외부에서는 부모의 `add*`와 `remove*`만 호출한다.
- 삭제 대상이 부모 컬렉션에 없는데 성공한 것처럼 처리하지 않는다. 멱등 삭제가 필요한 유스케이스라면 그 정책을 메서드명과 테스트로 명시한다.

### 4.2 일대일 관계는 양쪽 소유권을 모두 검사한다

`User`와 `Person`처럼 양쪽에서 접근하는 일대일 관계는 두 객체가 이미 다른 상대와 연결됐는지 모두 확인한다. 재귀 호출은 현재 연결 여부를 확인해 한 번만 일어나게 한다.

```java
this.person = requiredPerson;
if (requiredPerson.getUser() != this) {
    requiredPerson.attachUser(this);
}
```

### 4.3 JPA 매핑 기본값

Aggregate가 소유하는 자식 컬렉션:

```java
@OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderColumn(name = "sort_order") // 도메인상 순서가 있을 때만
private List<Child> children = new ArrayList<>();
```

자식에서 부모를 참조하는 필수 관계:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "parent_id", nullable = false)
private Parent parent;
```

- `ManyToOne`, `OneToOne`은 특별한 이유가 없으면 `LAZY`를 명시한다.
- 필수 관계는 `optional = false`와 `nullable = false`를 함께 둔다.
- `cascade = ALL`, `orphanRemoval = true`는 부모와 생명주기를 함께하는 자식에만 적용한다.
- `Genre`, `Person`처럼 공유되는 참조에는 무분별하게 cascade를 적용하지 않는다.
- 순서가 도메인 상태일 때만 `List`와 `@OrderColumn`을 사용한다.

---

## 5. 컬렉션 캡슐화

엔티티 내부에서는 변경 가능한 컬렉션을 유지하지만 외부에는 읽기 전용 view만 제공한다.

```java
private List<StoryboardScene> scenes = new ArrayList<>();

public List<StoryboardScene> getScenes() {
    return Collections.unmodifiableList(scenes);
}
```

외부 코드는 다음 작업을 할 수 없어야 한다.

```java
project.getScenes().add(scene);
project.getScenes().clear();
```

모든 변경은 `add*`, `remove*`, `replace*`, `reorder*`, `clear*`를 통한다. 클래스에 Lombok `@Getter`가 있어도 컬렉션 getter는 직접 선언해 덮어쓴다.

### 5.1 재정렬은 정확한 순열만 허용한다

전체 순서를 받는 API라면 요청이 기존 원소의 정확한 순열인지 확인한다.

- 요청 목록 자체가 `null`인지
- 목록 안에 `null`이 있는지
- ID 또는 key가 중복되는지
- 다른 Aggregate의 원소가 포함됐는지
- 기존 원소가 누락됐는지
- 저장되지 않아 ID가 없는 원소가 있는지

존재하지 않는 ID를 무시하거나 누락된 원소를 뒤에 자동으로 붙이지 않는다.

```java
if (requestedIds.size() != requested.size()
        || requested.size() != children.size()
        || !existingById.keySet().equals(requestedIds)) {
    throw new IllegalArgumentException(
            "ids must contain every child exactly once"
    );
}
```

### 5.2 교체는 기존 엔티티를 가능한 한 유지한다

`replaceSns`, `replaceProfileTags`, `replaceCards`처럼 전체 목록을 교체할 때 무조건 모두 삭제하고 다시 만들지 않는다.

1. 입력 전체를 먼저 검증한다.
2. ID 또는 정규화된 business key로 기존 원소를 찾는다.
3. 유지되는 원소는 같은 엔티티 인스턴스를 재사용한다.
4. 새 원소만 생성·연결한다.
5. 제외된 원소만 `detach`한다.
6. 최종 순서대로 컬렉션을 다시 구성한다.

이 방식은 불필요한 DELETE/INSERT, 식별자 변경, orphanRemoval 부작용을 줄인다.

외부 후처리가 필요하면 엔티티가 직접 I/O하지 않고 결과 객체를 반환한다.

```java
public record CardReplacementResult(List<String> obsoleteImageKeys) {
    public CardReplacementResult {
        obsoleteImageKeys = List.copyOf(obsoleteImageKeys);
    }
}
```

---

## 6. 검증과 정규화

### 6.1 엔티티는 항상 불변식을 지킨다

생성자와 모든 공개 변경 메서드가 같은 규칙을 적용해야 한다. DTO 검증을 통과하지 않는 내부 호출이나 테스트 코드에서도 잘못된 상태가 만들어지면 안 된다.

자주 사용하는 형태:

```java
private static <T> T require(T value, String fieldName) {
    if (value == null) {
        throw new IllegalArgumentException(fieldName + " is required");
    }
    return value;
}

private static String requireText(String value, String fieldName, int maxLength) {
    if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(fieldName + " is required");
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
        throw new IllegalArgumentException(fieldName + " is too long");
    }
    return trimmed;
}

private static String normalizeOptionalText(
        String value,
        String fieldName,
        int maxLength
) {
    if (value == null) return null;
    String trimmed = value.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.length() > maxLength) {
        throw new IllegalArgumentException(fieldName + " is too long");
    }
    return trimmed;
}
```

- 필수 문자열: `null`, blank를 거부하고 trim한다.
- 선택 문자열: `null`과 blank를 도메인 정책에 따라 `null`로 통일한다.
- 숫자와 시간: 허용 범위와 선후 관계를 검증한다.
- UUID: 필요하면 canonical UUID 형식까지 검증한다.
- URL·태그·이메일·username처럼 규칙이 복잡하거나 재사용되면 Value Object 또는 전용 정책 클래스로 분리한다.

### 6.2 길이와 기본 정책은 상수화한다

```java
public static final int TITLE_MAX_LENGTH = 120;

@Column(nullable = false, length = TITLE_MAX_LENGTH)
private String title;
```

같은 상수를 엔티티 검증과 `@Column(length = ...)`에서 사용한다. DTO의 `@Size`도 같은 값과 일치해야 한다.

- 외부 DTO가 참조해야 하면 `public static final`
- 엔티티 내부에서만 쓰면 `private static final`

### 6.3 정규화 값과 표시 값을 분리한다

중복 판정이나 검색에 정규화가 필요하면 원문과 정규화 값을 함께 저장한다.

예:

- `Genre.name` / `Genre.normalized`
- `ProfileTag.rawText` / `ProfileTag.normalized`
- `User.username` / `User.usernameNormalized`

정규화는 모든 호출 계층에서 반복하지 않고 `GenreName`, `Username` 같은 Value Object나 엔티티의 단일 함수에서 수행한다.

### 6.4 검증은 세 겹으로 보완한다

상세한 계층별 검증 기준은 [Onfilm 검증 흐름 컨벤션](validation-flow-convention.md)을 따른다.

| 계층 | 책임 |
|---|---|
| DTO | 요청 형식과 사용자에게 보여 줄 오류 메시지 |
| Entity / Value Object | 어떤 호출 경로에서도 지켜야 하는 도메인 불변식 |
| DB | 동시 요청까지 포함한 최종 무결성 보장 |

중복을 엔티티 컬렉션에서 검사했더라도 `@UniqueConstraint`를 함께 둔다. 애플리케이션의 사전 중복 조회만으로는 동시 요청 경쟁 조건을 막을 수 없다.

---

## 7. 상태 변경 메서드와 상태 머신

### 7.1 setter 대신 의미 있는 행위를 공개한다

```java
project.changeTitle(title);
genre.activate();
token.consume(now);
job.markProcessing(now);
```

필드명 중심의 범용 setter는 사용하지 않는다. 메서드 안에서 상태 전이와 관련 필드를 함께 변경한다.

### 7.2 상태 전이는 엔티티가 검증한다

`MediaEncodeJob`, `RefreshToken`, `MediaEncodeOutbox`처럼 상태가 있는 엔티티는 허용 전이를 엔티티 안에 둔다.

```text
REQUESTED -> PROCESSING -> DONE
                      \-> FAILED
```

- 허용되지 않은 전이는 `IllegalStateException`으로 거부한다.
- 상태 변경 시각은 서비스가 `Clock`으로 구해 엔티티에 전달한다.
- 엔티티 내부에서 시스템 시각을 직접 조회하지 않는다.
- 재전달이 가능한 명령은 같은 결과의 반복 호출만 멱등하게 허용한다.
- 서로 다른 결과로 상태를 되돌리는 호출은 거부한다.

동시 상태 변경을 감지해야 하는 엔티티에는 `@Version`을 사용하고 충돌 처리 정책을 서비스와 테스트에 둔다.

---

## 8. 저장소 key와 민감 정보

### 8.1 URL과 storage key의 의미를 섞지 않는다

DB에는 공개 URL보다 provider 독립적인 storage key를 저장하는 것을 기본으로 한다.

```java
private String avatarImageKey;
private String storageKey;
```

- `http://`, `https://` 값은 storage key 필드에서 거부한다.
- 사용자·Aggregate 소유권까지 필요한 경로 검증은 `StorageKeyPolicy`에서 수행한다.
- 엔티티는 길이, 필수 여부처럼 자기 상태만으로 판단할 수 있는 규칙을 담당한다.

### 8.2 민감한 필드는 노출을 명시적으로 통제한다

`User.encodedPassword`, token hash처럼 민감한 필드는 다음 원칙을 따른다.

- 원문 비밀번호와 원문 refresh token은 엔티티에 저장하지 않는다.
- 필드 의미가 드러나는 이름을 사용한다: `password`보다 `encodedPassword`.
- 직렬화 방지를 위해 `@JsonIgnore`를 적용한다.
- 필요한 getter만 명시적으로 작성한다.
- 로그에는 token, hash, password, secret을 남기지 않는다.

---

## 9. 트랜잭션과 외부 부수 효과

엔티티는 스토리지, Kafka, HTTP 같은 외부 시스템을 직접 호출하지 않는다.

DB 변경과 파일 삭제가 함께 필요한 경우:

1. 엔티티가 제거할 storage key를 결과 객체로 반환하거나 서비스가 변경 전 key를 수집한다.
2. Command Service가 `StorageFilesDeleteEvent`를 발행한다.
3. `@TransactionalEventListener(phase = AFTER_COMMIT)`에서 파일을 삭제한다.
4. 삭제 실패는 DB 트랜잭션을 되돌릴 수 없으므로 key와 예외를 로그로 남긴다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void deleteFiles(StorageFilesDeleteEvent event) {
    for (String key : event.keys()) {
        try {
            storageService.delete(key);
        } catch (Exception exception) {
            log.error("Failed to delete storage file after transaction commit. key={}",
                    key, exception);
        }
    }
}
```

반대로 외부 작업 성공과 DB 저장을 원자적으로 다뤄야 하는 비동기 메시지는 Transactional Outbox처럼 별도 패턴을 사용한다. 엔티티 메서드 하나로 DB와 외부 시스템의 원자성을 해결하려 하지 않는다.

보안 기록처럼 외부에 반환할 예외 때문에 반드시 남겨야 하는 DB 변경은 별도의 `REQUIRES_NEW` 트랜잭션을 검토한다. 이는 일반 상태 변경의 기본값이 아니라 롤백 요구사항이 명확한 경우에만 사용한다.

---

## 10. 서비스 계층의 역할

Command Service는 다음을 담당한다.

- 현재 사용자와 권한 확인
- Aggregate Root 조회
- 외부 정책 검증
- 엔티티 도메인 메서드 호출
- 트랜잭션 경계
- 이벤트 발행과 외부 시스템 orchestration

Entity는 다음을 담당한다.

- 자기 상태의 유효성
- 상태 전이
- 자식 생성과 컬렉션 불변식
- 양방향 연관관계 일관성

Query Service는 조회와 DTO 변환을 담당한다. 쓰기 기능이 커지면 `*QueryService`와 `*CommandService`로 분리한다.

```java
@Service
@Transactional
public class StoryboardCommandService {
    public StoryboardScene createScene(...) {
        Person person = findCurrentPerson();
        StoryboardProject project = findProject(person, projectId);
        return project.addScene(title, scriptHtml);
    }
}
```

영속 상태의 Aggregate를 트랜잭션 안에서 변경했다면 dirty checking을 사용한다. 단순히 변경 내용을 저장하기 위해 `repository.save(entity)`를 반복 호출하지 않는다.

---

## 11. 예외 정책

- 잘못된 인자와 형식: `IllegalArgumentException`
- 현재 상태나 소유권 때문에 수행 불가: `IllegalStateException`
- API에서 구분해야 하는 도메인 오류: 의미 있는 custom exception
- 조회 실패: `*NotFoundException`
- 중복이 불변식 위반이면 조용히 무시하지 않는다.
- 중복 입력을 병합하는 것이 명시된 정책이면 `putIfAbsent`처럼 의도가 보이게 구현하고 테스트한다.

같은 메서드에서 일부 입력만 반영한 뒤 예외가 발생하지 않도록 전체 입력을 먼저 검증하고 상태를 변경한다.

---

## 12. 테스트 기준

### 12.1 순수 엔티티 단위 테스트

새 엔티티마다 최소한 다음을 검증한다.

- 정상 생성과 초기 상태
- 필수 값의 `null`·blank 거부
- 경계 길이 허용과 초과 길이 거부
- trim·정규화 결과
- 정상 상태 변경
- 허용되지 않은 상태 전이 거부
- 상태 변경의 멱등성 정책

부모·자식 관계가 있으면 다음을 추가한다.

- 부모 메서드로 자식 생성
- 추가 시 양방향 연결
- 다른 부모로 재할당 거부
- 동일 자식과 business key 중복 거부
- 제거 시 양방향 해제
- 소속되지 않은 자식 제거 거부
- 외부 컬렉션 변경 불가
- replace 시 유지 대상의 ID/인스턴스 보존
- reorder의 정상·중복·누락·외부 ID 거부

### 12.2 JPA 영속성 테스트

단위 테스트만으로 확인할 수 없는 내용을 별도로 검증한다.

- cascade 저장
- orphanRemoval DELETE
- `@OrderColumn` 순서 저장과 재조회
- unique constraint
- `@Version` optimistic lock
- LAZY 연관관계와 필요한 fetch 전략

### 12.3 서비스 테스트

- 권한과 Aggregate 소유권 확인
- 엔티티 메서드 호출 순서
- 트랜잭션 rollback
- AFTER_COMMIT 이벤트
- 외부 시스템 실패 처리
- DB unique constraint 경쟁 조건을 API 오류로 변환하는지

---

## 13. 새 부모·자식 엔티티 템플릿

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Parent {
    public static final int NAME_MAX_LENGTH = 120;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    private List<Child> children = new ArrayList<>();

    private Parent(String name) {
        this.name = requireName(name);
    }

    public static Parent create(String name) {
        return new Parent(name);
    }

    public Child addChild(String value) {
        Child child = Child.create(value);
        addChild(child);
        return child;
    }

    void addChild(Child child) {
        Child requiredChild = require(child, "child");
        if (children.contains(requiredChild)) {
            throw new IllegalArgumentException("duplicate child");
        }
        requiredChild.attachParent(this);
        children.add(requiredChild);
    }

    public void removeChild(Child child) {
        Child requiredChild = require(child, "child");
        if (!children.remove(requiredChild)) {
            throw new IllegalArgumentException("child does not belong to parent");
        }
        requiredChild.detachParent(this);
    }

    public void changeName(String name) {
        this.name = requireName(name);
    }

    public List<Child> getChildren() {
        return Collections.unmodifiableList(children);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String value = name.trim();
        if (value.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name is too long");
        }
        return value;
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
```

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child {
    private static final int VALUE_MAX_LENGTH = 120;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = VALUE_MAX_LENGTH)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    private Child(String value) {
        this.value = requireValue(value);
    }

    static Child create(String value) {
        return new Child(value);
    }

    void attachParent(Parent parent) {
        Parent requiredParent = require(parent, "parent");
        if (this.parent != null && this.parent != requiredParent) {
            throw new IllegalStateException("child already belongs to another parent");
        }
        this.parent = requiredParent;
    }

    void detachParent(Parent parent) {
        if (this.parent == parent) this.parent = null;
    }

    void changeValue(String value) {
        this.value = requireValue(value);
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > VALUE_MAX_LENGTH) {
            throw new IllegalArgumentException("value is too long");
        }
        return trimmed;
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
```

템플릿은 출발점이다. 실제 도메인의 중복 기준, 최대 개수, 삭제 정책, 순서 여부를 반드시 추가한다.

---

## 14. 리뷰 체크리스트

### 생성과 캡슐화

- [ ] JPA 기본 생성자가 `protected`인가?
- [ ] 비즈니스 생성자가 `private`인가?
- [ ] 생성 의도가 정적 팩토리 이름에 드러나는가?
- [ ] setter 없이 도메인 메서드로만 변경하는가?
- [ ] 컬렉션을 읽기 전용으로 노출하는가?

### 검증

- [ ] 생성과 변경에서 동일한 불변식을 지키는가?
- [ ] 필수·선택 문자열의 blank 정책이 명확한가?
- [ ] 길이 상수를 엔티티 검증과 컬럼에 같이 사용하는가?
- [ ] 정규화와 중복 기준이 한곳에 정의됐는가?
- [ ] DTO·Entity·DB 제약이 서로 보완하는가?

### 연관관계

- [ ] 외부가 부모 메서드로 자식을 생성하는가?
- [ ] `add/remove`가 `attach/detach`까지 수행하는가?
- [ ] 다른 부모로 재할당할 수 없는가?
- [ ] cascade와 orphanRemoval이 실제 생명주기 소유권과 일치하는가?
- [ ] 공유 엔티티에 불필요한 cascade가 없는가?
- [ ] 순서가 도메인 상태일 때 `@OrderColumn`을 사용했는가?

### 상태와 트랜잭션

- [ ] 상태 전이를 엔티티가 검증하는가?
- [ ] 필요한 엔티티에 `@Version`이 있는가?
- [ ] 외부 I/O가 엔티티 밖에 있는가?
- [ ] 파일 삭제 같은 비가역 작업이 commit 이후 실행되는가?
- [ ] 반드시 보존해야 하는 기록의 rollback 정책이 명확한가?

### 테스트

- [ ] 엔티티 경계값과 불변식 테스트가 있는가?
- [ ] 양방향 관계와 컬렉션 캡슐화 테스트가 있는가?
- [ ] orphanRemoval, 순서, unique, version 영속성 테스트가 있는가?
- [ ] 동시 요청과 외부 시스템 실패 경로를 서비스에서 검증했는가?

---

## 15. 피해야 할 패턴

```java
@Setter
public class Entity { ... }
```

```java
entity.getChildren().add(child);
entity.getChildren().clear();
```

```java
child.setParent(parent);
parent.getChildren().add(child);
```

```java
// 존재하지 않는 ID를 무시하고 누락 원소를 뒤에 붙이는 재정렬
requestedIds.forEach(id -> {
    if (byId.containsKey(id)) reordered.add(byId.remove(id));
});
reordered.addAll(byId.values());
```

```java
// DB commit 전에 복구하기 어려운 외부 삭제 실행
storageService.delete(key);
repository.delete(entity);
```

```java
// 서비스가 엔티티 불변식과 연관관계를 대신 관리
Child child = new Child();
child.setParent(parent);
parent.getChildren().add(child);
```

이 패턴들은 엔티티를 단순 데이터 묶음으로 만들고, 호출 위치마다 검증과 연관관계 처리 방식이 달라지게 한다.
