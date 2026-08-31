# Entity 메서드 네이밍 컨벤션

## 1. 목적

같은 상태 변경을 `update`, `change`, `apply` 등 서로 다른 이름으로 표현하면 메서드의 책임과 호출 계층을 파악하기 어렵다.

이 문서는 서비스와 엔티티의 상태 변경, 양방향 연관관계 편의 메서드의 이름과 호출 방향을 통일하기 위한 규칙이다.

핵심 원칙은 다음과 같다.

- Controller·Service의 유스케이스는 `update`
- Entity의 공개 상태 변경은 `change`
- Entity 내부의 공통 반영 로직은 `apply`
- 부모의 컬렉션 변경은 `add` / `remove`
- 자식의 단일 연관관계 변경은 `attach` / `detach`

---

## 2. 상태 변경 메서드

### 2-1. `update`: 유스케이스 수행

`update`는 Controller와 Service에서 사용한다. HTTP 요청이나 어플리케이션 유스케이스 전체를 수행한다는 의미다.

예:

- `PersonController.updatePerson()`
- `PersonService.updatePerson()`
- `PersonReadService.updateStoryboardProject()`
- `MovieService.updateFilmographyItemPrivacy()`

Service의 `update` 메서드는 엔티티를 조회하고, 필요한 권한과 입력을 검증하고, Entity의 `change` 메서드를 호출한다.

```java
@Transactional
public void updateFilmographyItemPrivacy(Long movieId, boolean isPrivate) {
    MoviePerson moviePerson = findMoviePerson(movieId);
    moviePerson.changePrivacy(isPrivate);
}
```

### 2-2. `change`: 엔티티의 의미 있는 상태 변경

`change`는 Entity의 `public` 상태 변경 메서드에 사용한다. 단순 필드 대입보다 도메인 규칙을 거쳐 상태를 바꾼다는 의미를 드러낸다.

예:

- `Movie.changeBasicInfo()`
- `Movie.changeMovieUrl()`
- `MoviePerson.changeSortOrder()`
- `MoviePerson.changePrivacy()`
- `StoryboardProject.changeTitle()`
- `StoryboardScene.changeScriptHtml()`

```java
public void changePrivacy(boolean isPrivate) {
    this.isPrivate = isPrivate;
}
```

### 2-3. `apply`: 검증된 값을 실제 필드에 반영

`apply`는 생성자와 공개 변경 메서드가 공통으로 사용하는 Entity 내부 구현에 사용한다.

- 일반적으로 `private`으로 둔다.
- Controller와 Service에서 직접 호출하지 않는다.
- 검증, 정규화, 필드 대입을 한 곳에서 처리한다.
- 생성과 변경이 같은 규칙을 사용한다는 것을 보장한다.

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

공통 로직이 없는 간단한 변경에 `apply` 메서드를 의미 없이 추가하지 않는다.

### 2-4. 더 명확한 도메인 동사가 있으면 우선한다

`change`는 모든 Entity 메서드에 기계적으로 붙이는 prefix가 아니다. 행위를 더 정확하게 설명하는 동사가 있으면 그 이름을 우선한다.

예:

- `MovieGenre.mapToGenre()`
- `Movie.clearGenres()`
- `Person.reorderGallery()`
- `Movie.addMoviePerson()`
- `Person.attachUser()`

`change` + 필드명은 별도의 도메인 동사로 표현하기 어려운 상태 변경에 사용한다.

---

## 3. 연관관계 편의 메서드

### 3-1. `add`: 부모의 컬렉션에 자식 추가

`add`는 일대다 관계에서 부모 Entity가 자식 컬렉션을 변경할 때 사용한다.

예:

- `Movie.addMoviePerson()`
- `Movie.addMovieGenre()`
- `StoryboardProject.addScene()` (추가 시 권장 이름)
- `StoryboardScene.addCard()` (추가 시 권장 이름)

`add` 메서드는 다음 책임을 가진다.

1. null 및 입력 검증
2. 중복과 도메인 불변식 검증
3. 자식의 `attach` 호출
4. 부모 컬렉션에 자식 추가

```java
public void addMovieGenre(MovieGenre movieGenre) {
    MovieGenre requiredMovieGenre = require(movieGenre, "movieGenre");

    if (hasGenre(requiredMovieGenre.getNormalizedText())) {
        throw new IllegalArgumentException("duplicate movie genre");
    }

    requiredMovieGenre.attachMovie(this);
    genres.add(requiredMovieGenre);
}
```

### 3-2. `attach`: 자식이 부모 참조를 설정

`attach`는 자식 Entity의 단일 연관관계 필드를 설정할 때 사용한다.

예:

- `MoviePerson.attachMovie()`
- `MovieGenre.attachMovie()`
- `StoryboardScene.attachProject()`
- `StoryboardCard.attachScene()`

```java
void attachMovie(Movie movie) {
    Movie requiredMovie = require(movie, "movie");
    if (this.movie != null && this.movie != requiredMovie) {
        throw new IllegalStateException(
                "movieGenre already belongs to another movie"
        );
    }
    this.movie = requiredMovie;
}
```

`attach` 메서드는 가능하면 package-private으로 두어 외부에서 직접 호출하지 못하게 한다. 외부에서는 부모의 `add`만 호출한다.

```java
MovieGenre.createStandard(movie, standardGenre);
MovieGenre.createCustom(movie, customText);
```

```text
MovieGenre.createStandard() / createCustom()
        ↓
Movie.addMovieGenre()
        ↓
MovieGenre.attachMovie()
        ↓
Movie.genres.add()
```

팩토리 메서드가 관계 연결까지 책임지는 구조에서는 다음처럼 두 번 추가하지 않는다.

```java
// 잘못된 호출: create 내부에서 이미 추가됨
MovieGenre movieGenre = MovieGenre.createStandard(movie, genre);
movie.addMovieGenre(movieGenre);
```

### 3-3. `remove` / `detach`

- `remove`: 부모 컬렉션에서 자식을 제거한다.
- `detach`: 자식의 부모 참조를 해제한다.

양방향 연관관계를 해제할 때도 외부에서는 부모의 `remove`를 호출하고, `remove` 내부에서 자식의 `detach`를 호출한다.

`orphanRemoval = true`인 관계는 컬렉션에서 제거할 때 DB DELETE가 발생할 수 있으므로 트랜잭션 범위와 삭제 정책을 함께 확인한다.

### 3-4. `clear` / `reorder`

- `clear`: 단일 값을 비우거나 컬렉션 전체를 제거한다.
- `reorder`: 컬렉션의 순서를 변경한다.

예:

- `Movie.clearThumbnailUrl()`
- `Movie.clearGenres()`
- `Person.reorderGallery()`

---

## 4. 예외와 중복 처리 정책

### 4-1. 잘못된 호출은 조용히 무시하지 않는다

필수 값이 `null`이거나 도메인 불변식을 어긴 경우는 예외로 알린다.

```java
// 지양
if (movieGenre == null) return;

// 권장
MovieGenre requiredMovieGenre = require(movieGenre, "movieGenre");
```

선택 입력이 없는 것이 정상인 유스케이스에서만 `null` 또는 빈 컬렉션을 그대로 무시할 수 있다.

### 4-2. 중복 정책을 메서드마다 달리 두지 않는다

- 중복이 프로그래밍 오류이거나 불변식 위반이면 예외를 발생시킨다.
- 멱등적 추가가 명시적인 정책이면 이미 있는 값을 반환하거나 결과를 명확히 표현한다.
- 도메인 규칙은 Entity에서 검증하고, 데이터 무결성은 DB 유니크 제약으로도 보완한다.

예:

- `MoviePerson`: 같은 Movie·Person 참여 중복을 예외로 처리
- `MoviePersonRole`: 같은 참여 관계 안의 역할 중복을 예외로 처리
- `MovieGenre`: 같은 영화의 정규화 장르 중복을 예외와 DB 제약으로 처리

---

## 5. 메서드 이름 요약

| Prefix | 의미 | 주요 위치 | 공개 범위 |
|---|---|---|---|
| `create` | 규칙에 따라 Entity 생성 | Entity 정적 팩토리 | `public static` |
| `update` | 유스케이스 전체 수행 | Controller, Service | `public` |
| `change` | Entity의 의미 있는 상태 변경 | Entity | `public` |
| `apply` | 검증·정규화·필드 반영 공통화 | Entity 내부 | `private` |
| `add` | 부모 컬렉션에 자식 추가 | 부모 Entity | `public` |
| `attach` | 자식의 단일 연관관계 설정 | 자식 Entity | package-private 권장 |
| `remove` | 부모 컬렉션에서 자식 제거 | 부모 Entity | `public` |
| `detach` | 자식의 단일 연관관계 해제 | 자식 Entity | package-private 권장 |
| `clear` | 단일 값 또는 컬렉션 전체 제거 | Entity | `public` |
| `reorder` | 컬렉션 순서 변경 | Entity | `public` |

---

## 6. 리뷰 체크리스트

새 Entity 메서드를 추가하거나 리뷰할 때 다음을 확인한다.

- Service의 유스케이스는 `update`, Entity 상태 변경은 `change`로 표현했는가?
- 생성과 변경이 같은 검증 로직을 사용한다면 `private apply*` 메서드로 공통화했는가?
- 양방향 관계의 양쪽 값을 한 번에 맞추는가?
- 외부에서는 부모의 `add` / `remove`만 호출하는가?
- 자식의 `attach` / `detach`는 필요 이상으로 공개되지 않았는가?
- 다른 부모로의 재할당을 방지하는가?
- 필수 값이나 중복을 조용히 무시하고 있지 않은가?
- Entity 검증을 DB 제약으로도 보완해야 하는가?
