# Movie ↔ Genre 매핑 엔티티 설계 정리

`Movie`와 `Genre`는 겉으로는 다대다 관계지만, 단순 `@ManyToMany` 대신 **`MovieGenre` 매핑 엔티티**로 분리했다.
왜 그렇게 설계했는지, 어떤 요구사항을 풀기 위함이었는지 정리한다.

---

## 한 줄 요약

> `Movie ↔ Genre`는 단순 N:M이 아니라 **사용자 입력 원문 보존 + 마스터 데이터 미존재 허용 + 사후 표준화 매핑** 이 필요했기 때문에, `MovieGenre` 매핑 엔티티로 풀었다. `MoviePerson`과 같은 "관계에 속성이 붙는 도메인" 패턴이다.

---

## 1. 문제 상황 — 단순 `@ManyToMany`로는 풀 수 없던 3가지

| # | 요구사항 | 단순 `@ManyToMany`의 한계 |
|---|---|---|
| 1 | 사용자가 입력한 **장르 원문**을 그대로 보여줘야 함 ("SF", "에스에프", "공상과학") | 조인 테이블에 `movie_id`, `genre_id`만 있어서 입력 표현이 손실됨 |
| 2 | **표준 Genre 마스터에 없는 장르**도 등록 가능해야 함 | `Genre`에 미리 정의된 행만 FK로 연결 가능 → 신조어/마이그레이션 중 장르는 못 받음 |
| 3 | 나중에 표준 장르가 정해지면 기존 raw 텍스트를 **사후에 표준 Genre로 매핑** | 조인 테이블에 추가 컬럼이 없으니 표준화 작업의 상태 관리 불가 |

→ 세 요구사항 모두 "관계 자체에 데이터가 붙어야" 풀 수 있는 문제.
→ 그래서 매핑 엔티티 `MovieGenre`를 별도로 두고, **관계 자체를 도메인으로 다룸**.

---

## 2. 코드 근거

### 2-1. `MovieGenre` — 매핑 엔티티가 가진 속성

```java
@Entity
@Table(name = "movie_genre",
        indexes = {
                @Index(name = "idx_movie_genre_movie", columnList = "movie_id"),
                @Index(name = "idx_movie_genre_genre", columnList = "genre_id"),
                @Index(name = "idx_movie_genre_norm", columnList = "normalized_text")
        })
public class MovieGenre {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)        // ← nullable 허용 (마스터 미존재 OK)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    @Column(name = "raw_text", nullable = false, length = 60)
    private String rawText;                   // 사용자 입력 원문

    @Column(name = "normalized_text", nullable = false, length = 60)
    private String normalizedText;            // 검색/중복 제거용 정규화 텍스트

    // 나중에 표준 장르 매핑할 때 사용
    public void mapToGenre(Genre genre) {
        this.genre = genre;
    }
}
```

핵심 포인트:

- `genre`가 `optional = false`가 아님 → **`genre_id` nullable** → 표준 마스터에 없어도 등록 가능
- `rawText` (nullable=false) → 원문은 무조건 저장
- `normalizedText` (nullable=false) → 검색·중복 제거 기준
- `mapToGenre()` → 사후 표준 장르 매핑 진입점
- 인덱스 3개 → `movie_id` (영화별 조회), `genre_id` (장르 역방향 조회), `normalized_text` (정규화 텍스트 검색)

### 2-2. `MovieGenre.create()` — 매핑 시점의 의도

```java
public static MovieGenre create(Movie movie, Genre matchedGenreOrNull, String rawText) {
    if (movie == null) throw new IllegalArgumentException("movie is required");
    if (rawText == null || rawText.isBlank()) throw new IllegalArgumentException("rawText is required");

    String cleanedRaw = rawText.trim();
    String normalized = TextNormalizer.textNormalizer(cleanedRaw);
    if (normalized.isBlank()) throw new IllegalArgumentException("normalizedText is blank");

    return MovieGenre.builder()
            .movie(movie)
            .genre(matchedGenreOrNull)   // ✅ 있으면 연결, 없으면 null
            .rawText(cleanedRaw)
            .normalizedText(normalized)
            .build();
}
```

→ 변수명 `matchedGenreOrNull`이 설계 의도를 그대로 드러냄.
→ 매핑은 "사용자 입력 → 정규화 → (있으면) 표준 매칭 → 저장" 순서.

### 2-3. `Genre` 마스터 — 무결성 + 운영 안전성

```java
@Entity
@Table(name = "genre",
        uniqueConstraints = @UniqueConstraint(name = "uk_genre_normalized", columnNames = "normalized"))
public class Genre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 60)
    private String normalized;

    @Column(nullable = false)
    private boolean isActive = true;   // 삭제 대신 비활성화
}
```

핵심 포인트:

- `normalized`에 **unique constraint** → 마스터 자체의 중복 차단
- `isActive` 플래그 → 운영하지 않는 장르도 **물리 삭제 대신 비활성화** → 이미 그 장르로 등록된 영화 데이터를 깨뜨리지 않음

---

## 3. 설계 패턴 — `MoviePerson`과 동일

`Person ↔ Movie`도 동일한 패턴으로 `MoviePerson`을 두었다.

| 매핑 엔티티 | 관계에 붙는 속성 | 도메인 의도 |
|---|---|---|
| `MoviePerson` | `sortOrder`, `isPrivate`, `MoviePersonRole` 목록 | "내 프로필에서 이 영화를 어떤 역할·순서로 보여줄지" |
| `MoviePersonRole` | `role`, `castType`, `characterName` | "이 작품에서 맡은 하나의 역할과 배우 상세 정보" |
| `MovieGenre` | `rawText`, `normalizedText`, `genre`(nullable) | "사용자가 적은 장르 표현 + 표준 매칭 결과" |

> 공통 원칙: **관계 자체에 속성이 붙거나 도메인 의미가 있으면, 단순 `@ManyToMany`가 아니라 매핑 엔티티로 풀어 관계를 1급 도메인으로 다룬다.**

---

## 4. 만약 단순 `@ManyToMany`로 풀었다면 생겼을 문제

| 시나리오 | 단순 N:M | 매핑 엔티티 (현재) |
|---|---|---|
| 사용자가 "에스에프" 입력 | "SF"로 표준화되어 원문 손실 | rawText="에스에프" 저장, normalized="sf" 별도 보관 |
| 사용자가 신조어 "K-누아르" 입력 | Genre 마스터에 없어서 등록 거부 | `genre_id=null`로 우선 저장, 사후 매핑 |
| 운영자가 "K-누아르"를 표준 장르로 추가 | 기존 영화들과 자동 연결 안 됨 | `normalized_text` 인덱스로 일괄 조회 후 `mapToGenre()` 실행 |
| "느와르"와 "누아르"가 같은 장르라고 통합 | 데이터 마이그레이션 어려움 | normalized 기준으로 중복 묶고 한 표준 Genre로 매핑 |

---

## 5. 면접 답변 템플릿

### 30초 버전

> "Movie와 Genre는 겉으로는 다대다지만, 사용자가 입력한 장르 원문을 그대로 보존해야 했고, 표준 Genre 마스터에 없는 장르도 우선 받아야 했고, 나중에 표준 장르가 정해지면 사후에 매핑해야 했습니다. 단순 `@ManyToMany`로는 관계 자체에 데이터를 붙일 수 없어서, `MovieGenre`라는 매핑 엔티티로 분리하고 `rawText`, `normalizedText`, 그리고 nullable한 `genre` FK를 함께 보관하도록 설계했습니다. `MoviePerson`과 같은 원칙입니다."

### "왜 nullable FK까지 허용했냐"는 후속 질문이 오면

> "장르는 운영자가 미리 정의한 마스터 데이터로만 받기에는 한계가 있었습니다. 사용자는 다양한 표현으로 입력하고, 신조어도 등장합니다. 그래서 일단 raw 텍스트와 정규화 텍스트는 무조건 저장하고, 표준 Genre 마스터에 매칭되는 게 있을 때만 FK를 채우는 구조로 잡았습니다. 이후 `mapToGenre()` 메서드로 사후 표준화가 가능합니다."

### "Genre.isActive는 왜 두었냐"는 후속 질문이 오면

> "장르를 물리 삭제하면, 이미 그 장르로 등록된 영화 데이터의 FK가 깨집니다. 그래서 운영하지 않는 장르는 `isActive=false`로 비활성화만 하고, 기존 매핑은 그대로 유지해 데이터 정합성을 보호하는 정책으로 설계했습니다."

---

## 6. 관련 코드 위치

- [`MovieGenre.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/entity/MovieGenre.java)
- [`Genre.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/genre/entity/Genre.java)
- [`MoviePerson.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/entity/MoviePerson.java) — 같은 패턴 비교용
- [`TextNormalizer.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/common/util/TextNormalizer.java) — 정규화 로직

---

## 7. 한 줄 정리

> 관계가 단순한 연결인지, 속성이 붙는 1급 도메인인지를 구분하는 것이 매핑 엔티티 분리 의사결정의 핵심이다.
> `MovieGenre`는 사용자 자유 입력과 표준 마스터의 간극을 흡수하는 **버퍼 레이어** 역할을 한다.
