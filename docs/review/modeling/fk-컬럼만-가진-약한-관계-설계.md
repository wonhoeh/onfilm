# FK 컬럼만 가진 약한 관계 설계 정리

`RefreshToken.userId`, `MediaEncodeJob.movieId`, `MediaEncodeJob.requestedByUserId`는 모두 다른 엔티티를 참조하는 FK 성격의 값이지만, **JPA `@ManyToOne` 연관관계로 묶지 않고 plain Long 컬럼**으로만 저장한다.
왜 그렇게 설계했는지, ERD에서는 어떻게 표기해야 하는지 정리한다.

---

## 한 줄 요약

> 모든 FK 성격 데이터를 JPA 연관관계로 묶지 않는다. `RefreshToken`/`MediaEncodeJob`처럼 **도메인 독립성·수명주기 분리·작업 스냅샷**이 중요한 엔티티는 plain 컬럼으로만 저장해 ORM 결합을 끊는다. 관계는 도메인 레벨에 존재하지만, ORM 레벨에서는 의도적으로 분리한 것이다.

---

## 1. 핵심 통찰 — "관계"는 3가지 레벨에서 정의된다

| 레벨 | 정의 | 표현 |
|---|---|---|
| **도메인/논리** | 한 `User`는 여러 `RefreshToken`을 가질 수 있는가? | ERD 카디널리티 (1:N, N:M 등) |
| **DB 스키마** | FK 제약조건이 걸려 있는가? | DDL의 `FOREIGN KEY (...)` 절 |
| **ORM (JPA)** | 객체 그래프 탐색이 가능한가? | `@ManyToOne`, `@OneToMany` 어노테이션 |

흔한 오해:
> "JPA에 연관관계가 없으면 관계가 없는 것이다."

실제:
> **개념적으로 N:1 관계지만, 그 관계를 ORM으로 구현하지 않는 것**이 가능하고, 때로는 그게 더 좋은 설계다.

이 프로젝트의 약한 관계 2건:

- `RefreshToken` → `User` (N:1)
- `MediaEncodeJob` → `Movie`, `User` (각각 N:1)

→ 도메인 레벨로는 명확히 N:1, **ORM 레벨로는 의도적으로 끊음**.

---

## 2. 코드 근거

### 2-1. `RefreshToken` — userId만 plain 컬럼

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long userId;          // ← @ManyToOne, @JoinColumn 없음

    @Column(nullable = false, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;
    // ...
}
```

핵심: `userId`는 단순 `Long` 컬럼. JPA가 보기에 `User` 엔티티와 아무 관계도 없음.

### 2-2. `MediaEncodeJob` — movieId, requestedByUserId 모두 plain 컬럼

```java
@Entity
public class MediaEncodeJob {
    @Id @Column(nullable = false, length = 64)
    private String id;                       // 외부에서 발급한 jobId (UUID)

    @Column(nullable = false)
    private Long movieId;                    // ← @ManyToOne 없음

    @Column(nullable = false)
    private Long requestedByUserId;          // ← @ManyToOne 없음

    @Enumerated(EnumType.STRING)
    private MediaEncodeJobStatus status;     // REQUESTED → PROCESSING → DONE/FAILED

    @Column(nullable = false)
    private String sourceBucket;
    @Column(nullable = false)
    private String sourceKey;
    @Column(nullable = false)
    private String targetBucket;
    @Column(nullable = false)
    private String targetKey;
    // ...
}
```

핵심: 두 FK 성격 컬럼 모두 plain `Long`. 정적 팩토리 메서드 이름이 `requested(...)`로 "**작업 스냅샷**"임을 드러냄.

---

## 3. 왜 의도적으로 안 묶었나 — 4가지 이유

### 3-1. 도메인 독립성

`token` 패키지가 `user` 패키지의 엔티티를 import하지 않고도 단독으로 동작.
`kafka` 패키지(인코딩 작업)가 `movie`/`user` 엔티티에 의존하지 않음.

→ 패키지 간 결합도 감소. 토큰/인코딩 도메인이 다른 도메인 변경에 영향받지 않음.

### 3-2. 불필요한 로딩 회피

토큰 검증 시 `User` 엔티티 전체가 필요한가? — 아니, `userId`만 있으면 충분.
인코딩 작업 callback 시 `Movie`/`User` 객체가 필요한가? — 아니, 상태 갱신과 결과 경로 저장만 하면 됨.

→ LAZY로 설정해두면 사실 차이 없지만, **연관관계 자체가 없으면 프록시 객체 생성·N+1 가능성 자체가 사라짐**.

### 3-3. 수명주기 분리 (cascade 부작용 방지)

`@ManyToOne` + `CascadeType.ALL`로 묶으면:
- `User` 삭제 시 토큰이 자동 cascade로 사라짐
- `Movie` 삭제 시 작업 이력이 사라짐 → **작업 이력은 감사/로그용으로 남아야 하는데 자동 삭제됨**

plain 컬럼 방식:
- `User` 삭제 시 토큰 삭제는 **명시적 비즈니스 로직**으로 처리 (예: 탈취 감지 시 해당 userId의 토큰 일괄 삭제)
- `Movie` 삭제와 무관하게 작업 이력 보존 가능

### 3-4. 작업 스냅샷 의미론

`MediaEncodeJob`은 **"작업 시점의 상태를 박제"** 하는 엔티티다.

- 작업 시점의 `movieId`, `sourceBucket`, `sourceKey`를 저장
- 이후 `Movie`가 수정/삭제되어도 작업은 그대로 남아야 함
- 운영자가 "어떤 영화에 대한 작업이었는지" 추적하려면 그 시점의 `movieId`만 있으면 됨

→ `@ManyToOne(Movie)`으로 묶으면 "현재의 Movie"를 가리키게 되는데, 작업 도메인이 원하는 건 "그때의 Movie ID"임. **시점 의미론이 다름**.

---

## 4. ERD 표기법 — 까마귀발 + 점선

도메인 레벨로는 명확히 관계가 있으므로 ERD에는 표기하되, **선 자체를 점선**으로 그려 약한 관계임을 표시.

```
User ─}o─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤├─ RefreshToken
   (실선 아님, 점선)
   카디널리티는 그대로 1:N
```

| 관계 | 카디널리티 | 선 스타일 | 비고 |
|---|---|---|---|
| `Person` ↔ `MoviePerson` | 1:N | 실선 | JPA `@ManyToOne` 있음 |
| `User` ↔ `Person` | 1:1 | 실선 | JPA `@OneToOne` 있음 |
| `User` ↔ `RefreshToken` | 1:N | **점선** | JPA 연관관계 없음 (userId 컬럼만) |
| `Movie` ↔ `MediaEncodeJob` | 1:N | **점선** | JPA 연관관계 없음 (movieId 컬럼만) |
| `User` ↔ `MediaEncodeJob` | 1:N | **점선** | JPA 연관관계 없음 (requestedByUserId 컬럼만) |

면접관 질문 대비:
> "왜 일부 선만 점선이에요?"
> → "도메인 레벨로는 관계가 있지만 JPA 연관관계로 묶지 않은 약한 관계입니다. 토큰/인코딩 도메인을 독립시키고 cascade 부작용을 막기 위한 의도적인 설계입니다."

---

## 5. 만약 `@ManyToOne`으로 묶었다면 생겼을 문제

| 시나리오 | `@ManyToOne` 묶음 | plain 컬럼 (현재) |
|---|---|---|
| 토큰 검증 시 사용자 정보 로딩 | 프록시 객체 생성, LAZY 풀리면 추가 쿼리 | userId 하나만 갖고 처리 |
| `User` 삭제 후 토큰 처리 | cascade 정책 따라감 (실수로 남거나 사라짐) | 명시적 비즈니스 로직 |
| `token` 패키지 단독 빌드/테스트 | `user` 패키지 의존 | 독립 가능 |
| `Movie` 삭제 후 작업 이력 | cascade로 사라질 위험 | 그대로 보존 |
| `Movie` 변경(제목/경로) 후 과거 작업 표시 | "현재 Movie" 기준으로 표시됨 | 작업 당시 스냅샷 그대로 |

---

## 6. 트레이드오프 — 약한 관계의 단점

균형 잡힌 시각도 정리:

| 단점 | 완화 방법 |
|---|---|
| DB 레벨 FK 제약조건이 없으면 무결성 깨질 수 있음 | 비즈니스 로직에서 검증 / 필요시 DB에 FK는 추가 가능 (ORM 연관관계와 별개) |
| 객체 그래프 탐색 불가 → `userId`로 User를 또 조회해야 하는 경우 발생 | 별도 Service 메서드로 명시적 조회 |
| ORM이 자동으로 join 안 해줌 | 필요한 곳에서 명시적 join 쿼리 작성 |

→ 단점들이 있지만, **도메인 독립성과 수명주기 분리 이득이 더 크다고 판단**된 경우 plain 컬럼이 맞음.

---

## 7. 면접 답변 템플릿

### 30초 버전

> "모든 FK 성격 데이터를 JPA 연관관계로 묶지는 않았습니다. `RefreshToken`은 `userId`만 컬럼으로 저장하고 `User`와 `@ManyToOne`을 두지 않았는데, 토큰 도메인을 인증 처리 중심으로 단순하게 유지하기 위해서입니다. `MediaEncodeJob`도 동일한 정책으로 `movieId`, `requestedByUserId`를 컬럼으로만 저장했습니다. 이쪽은 작업 시점의 상태를 박제하는 스냅샷 엔티티 성격이라, 이후 `Movie`나 `User`가 변경되어도 작업 이력은 그대로 남아야 했기 때문입니다."

### "관계가 없다는 거예요?" 후속 질문이 오면

> "관계는 있습니다 — 도메인 레벨로는 명확히 N:1 입니다. 다만 그 관계를 ORM 레벨로 구현하지 않았을 뿐입니다. 관계는 도메인/DB/ORM 세 레벨에서 따로 결정할 수 있는데, 토큰과 인코딩 작업 도메인은 ORM 레벨 결합을 끊는 게 도메인 독립성과 수명주기 분리에 더 유리하다고 판단했습니다."

### "ERD에는 그래서 그릴 거예요 말 거예요?" 후속 질문이 오면

> "그립니다. 카디널리티는 그대로 1:N으로 표기하되, 선 자체를 점선으로 그려서 JPA 연관관계가 없는 약한 관계임을 구분합니다."

### "FK 제약조건은 DB에 걸었나요?" 후속 질문이 오면

> "이 프로젝트에서는 ORM 어노테이션으로 FK를 선언하지 않아 Hibernate 자동 DDL에는 들어가지 않습니다. 운영 시에는 필요에 따라 DB 마이그레이션으로 FK 제약을 추가할 수 있는데, ORM 연관관계와 DB FK는 별개 선택입니다."

---

## 8. 같은 패턴 — 어디에 적용했나

| 엔티티 | FK 성격 컬럼 | 도메인 의도 |
|---|---|---|
| `RefreshToken` | `userId` | 인증 도메인 독립, 토큰 단독 처리 |
| `MediaEncodeJob` | `movieId`, `requestedByUserId` | 작업 시점 스냅샷, 인코딩 도메인 독립 |

반대로 **JPA 연관관계로 묶은 경우** (참고용):
- `Person ↔ User`: 1:1 양방향 (`@OneToOne`) — 프로필 편집 시 인증 정보까지 자연스럽게 연결
- `Person ↔ MoviePerson`, `Movie ↔ MoviePerson`: 1:N (`@ManyToOne`) — 매핑 엔티티 본연의 역할
- `Person ↔ StoryboardProject/Scene`: 1:N (`@ManyToOne` + `cascade`) — 소유 관계가 명확

→ **"엔티티 책임에 따라 매핑 전략을 선택"** 한다는 일관된 기준이 적용됨.

---

## 9. 관련 코드 위치

- [`RefreshToken.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/token/entity/RefreshToken.java)
- [`MediaEncodeJob.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeJob.java)
- 참고: 강한 연관관계 예시 — [`Person.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/entity/Person.java), [`MoviePerson.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/entity/MoviePerson.java)

---

## 10. 한 줄 정리

> "관계가 있다"와 "JPA 연관관계로 묶었다"는 다른 차원의 이야기다.
> 도메인 독립성·수명주기 분리·작업 스냅샷이 중요한 엔티티는 plain 컬럼으로만 두고 ORM 결합을 끊는 것이 더 좋은 설계일 수 있다.
