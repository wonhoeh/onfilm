# 다이어그램 참고 (직접 그릴 때 레퍼런스용)

도메인만 추린 ERD와 단순화된 시스템 아키텍처입니다.
draw.io / Excalidraw / Whimsical 등에서 그릴 때 참고하세요.

---

## 1. ERD — 도메인 엔티티 + 연관관계

### 전체 다이어그램

```
   [인증 도메인]                            [프로필 / 영화 도메인]

  ┌──────────────────┐                 ┌──────────────────────┐
  │  RefreshToken    │                 │      Person          │
  ├──────────────────┤                 ├──────────────────────┤
  │ id (PK)          │                 │ id (PK)              │
  │ user_id          │                 │ publicId (UK, UUID)  │
  │ token_hash       │                 │ name                 │
  │ revoked_at       │                 │ profile_image_url    │
  │ version          │                 │ filmography_private  │
  └────────┬─────────┘                 └──┬──────┬──────────┬─┘
           │ N                            │ 1    │ 1        │ 1
           │ (userId 컬럼만,              │      │          │
           │  JPA 연관 X)                 │      │          │
           │ 1                            │      │          │
  ┌────────┴─────────┐                    │      │          │
  │      User        │  1            1    │      │          │
  │ id (PK)          │────────────────────┘      │          │
  │ email (UK)       │   (1 : 1)                 │          │
  │ password         │                           │          │
  │ person_id (FK)   │                           │          │
  └──────────────────┘                           │ N        │ N
                                                 ▼          ▼
                                  ┌──────────────────┐  ┌────────────────────┐
                                  │  MoviePerson     │  │ StoryboardProject  │
                                  │  (매핑 엔티티)   │  ├────────────────────┤
                                  ├──────────────────┤  │ id (PK)            │
                                  │ id (PK)          │  │ person_id (FK)     │
                                  │ movie_id (FK)    │  │ title              │
                                  │ person_id (FK)   │  │ sort_order         │
                                  │ role             │  └─────────┬──────────┘
                                  │ cast_type        │            │ 1
                                  │ character_name   │            │
                                  │ sort_order       │            │ N
                                  │ is_private       │            ▼
                                  └────────┬─────────┘  ┌────────────────────┐
                                           │ N          │ StoryboardScene    │
                                           │            ├────────────────────┤
                                           │ 1          │ id (PK)            │
                                  ┌────────┴─────────┐  │ project_id (FK)    │
                                  │     Movie        │  │ title              │
                                  ├──────────────────┤  │ sort_order         │
                                  │ id (PK)          │  └────────────────────┘
                                  │ title            │
                                  │ movie_url        │
                                  │ thumbnail_url    │
                                  └────────┬─────────┘
                                           │ N
                                           │ (MovieGenre 매핑)
                                           │ M
                                  ┌────────┴─────────┐
                                  │     Genre        │
                                  ├──────────────────┤
                                  │ id (PK)          │
                                  │ name             │
                                  └──────────────────┘
```

### 관계 요약

| # | 관계 | 카디널리티 | 구현 | 비고 |
|---|---|---|---|---|
| 1 | `User` ─ `Person` | **1 : 1** | `User.person_id` (FK) | 인증과 공개 프로필 분리 |
| 2 | `User` ─ `RefreshToken` | **1 : N** | `RefreshToken.user_id` (컬럼만) | JPA 연관관계 X |
| 3 | `Person` ─ `MoviePerson` | **1 : N** | `MoviePerson.person_id` (FK) | 필모그래피 매핑 |
| 4 | `Movie` ─ `MoviePerson` | **1 : N** | `MoviePerson.movie_id` (FK) | 작품 참여자 매핑 |
| 5 | `Person` ─ `Movie` | **N : M** | (`MoviePerson` 경유) | 속성을 가진 관계 |
| 6 | `Movie` ─ `Genre` | **N : M** | (`MovieGenre` 경유, 도식 생략) | 점선 권장 |
| 7 | `Person` ─ `StoryboardProject` | **1 : N** | `StoryboardProject.person_id` (FK) | 사람의 프로젝트 목록 |
| 8 | `StoryboardProject` ─ `StoryboardScene` | **1 : N** | `StoryboardScene.project_id` (FK) | 프로젝트의 씬 목록 |

### 그릴 때 배치 팁

- **좌측 세로**: `RefreshToken — User` (인증 도메인 그룹)
- **중앙**: `Person`을 허브로 두고 → `MoviePerson — Movie — Genre`는 우측, `StoryboardProject — StoryboardScene`은 아래로
- **카디널리티 라벨**: 1, N, M을 선 양 끝에 명시 (1:N이면 `1` 쪽이 부모)
- **점선 vs 실선**: JPA 연관관계가 있는 것은 실선, `RefreshToken→User`처럼 컬럼만 가진 관계는 점선으로 구분
- **N:M 표기**: `Person — Movie`, `Movie — Genre`는 중간 매핑 엔티티(`MoviePerson`, `MovieGenre`)를 명시해두면 면접관이 좋아함

### 까마귀발(Crow's Foot) 표기법

draw.io / Excalidraw / dbdiagram.io 등에서 ERD 그릴 때 표준으로 쓰이는 표기법입니다. 선의 끝 모양으로 카디널리티와 필수/선택 여부를 동시에 표현합니다.

#### 기호 의미

| 기호 (선 끝 모양) | 의미 |
|---|---|
| `─┤├─` (이중 막대) | **정확히 하나** (mandatory one) — "반드시 1개" |
| `─o├─` (원 + 막대) | **0 또는 1** (optional one) — "있을 수도 없을 수도" |
| `─}├─` (까마귀발 + 막대) | **1개 이상** (mandatory many) — "최소 1개, 여러 개" |
| `─}o─` (까마귀발 + 원) | **0개 이상** (optional many) — "없거나 여럿" |

> ⚠️ **읽는 법이 헷갈리기 쉬운 부분**:
> - 선 끝에 붙은 기호는 **"그 엔티티가 몇 개인가"** 를 나타냄
> - User 1명이 RefreshToken 여러 개를 갖는 1:N 관계라면 → **까마귀발은 RefreshToken(N) 쪽에**, 막대기는 User(1) 쪽에 붙음
> - 즉 까마귀발은 항상 **"N(많은) 쪽 엔티티"** 에 그린다고 외우면 됨

> 선 끝의 **두 개 표기 구성**:
> - 안쪽(엔티티에 가까운 쪽) = 카디널리티 (`|` = 1, `}` = many)
> - 바깥쪽(중간 쪽) = 필수/선택 (`|` = 필수, `o` = 선택)

#### 이 프로젝트 ERD에 적용한 표기

| # | 관계 | 한쪽 끝 | 반대쪽 끝 | 까마귀발 표기 |
|---|---|---|---|---|
| 1 | `User` ─ `Person` (1:1) | User: 1개 (반드시) | Person: 0 또는 1개 | `User ─┤├──────o├─ Person` |
| 2 | `User` ─ `RefreshToken` (1:N) | User: 1개 (반드시) | RefreshToken: 0개 이상 | `User ─┤├──────}o─ RefreshToken` |
| 3 | `Person` ─ `MoviePerson` (1:N) | Person: 1개 (반드시) | MoviePerson: 0개 이상 | `Person ─┤├──────}o─ MoviePerson` |
| 4 | `Movie` ─ `MoviePerson` (1:N) | Movie: 1개 (반드시) | MoviePerson: 0개 이상 | `Movie ─┤├──────}o─ MoviePerson` |
| 5 | `Movie` ─ `MovieGenre` (1:N) | Movie: 1개 (반드시) | MovieGenre: 0개 이상 | `Movie ─┤├──────}o─ MovieGenre` |
| 6 | `MovieGenre` ─ `Genre` (N:1, nullable) | MovieGenre: 0개 이상 | Genre: **0 또는 1개** (nullable!) | `MovieGenre ─}o──────o├─ Genre` |
| 7 | `Person` ─ `StoryboardProject` (1:N) | Person: 1개 (반드시) | Project: 0개 이상 | `Person ─┤├──────}o─ StoryboardProject` |
| 8 | `StoryboardProject` ─ `StoryboardScene` (1:N) | Project: 1개 (반드시) | Scene: 0개 이상 | `Project ─┤├──────}o─ StoryboardScene` |

> 표 읽는 법: "한쪽 끝" 컬럼의 카디널리티 = User/Movie/Person 등 부모 엔티티에 가까운 쪽 기호.
> 1:N 관계에서 까마귀발은 항상 **N 쪽 (자식 엔티티 쪽)** 에 위치.

#### 주의할 포인트 (면접 어필 가능)

1. **`MovieGenre ─ Genre`는 선택적(optional)** — `MovieGenre.genre_id`가 nullable이라서 Genre 쪽 끝에 `─o├` (0 또는 1) 표기.
   - 사용자가 입력한 장르가 표준 `Genre` 마스터에 없을 수도 있기 때문 (사후 매핑 가능).
   - 다른 N:1 관계의 부모 쪽처럼 `─┤├` (필수 1)이 아님!

2. **`User ─ Person`은 1:1이지만 비대칭** — `User` 쪽은 반드시 `Person`을 갖지만, `Person`은 `User` 없이도 존재 가능한 설계라면 `─o├` 사용.
   - 둘 다 필수면 양쪽 모두 `─┤├` 사용.

3. **`User ─ RefreshToken`은 JPA 연관관계가 아님** — 까마귀발은 그대로 적용하되, 선 자체는 **점선**으로 그려서 `userId` 컬럼만 가진 비-JPA 관계임을 표시.

4. **매핑 엔티티(MoviePerson, MovieGenre) 양쪽 끝은 거의 항상 `─┤├`** — 매핑 엔티티는 양쪽 부모가 필수이기 때문 (`MovieGenre`의 `genre`는 예외).

#### 도구별 표기 방법

| 도구 | 까마귀발 지원 |
|---|---|
| **draw.io** | Shape → Entity Relation → 화살표 끝 스타일 변경 (`ERmany`, `ERone`, `ERmandOne`, `ERmandMany`) |
| **dbdiagram.io** | DBML 문법에서 자동 적용 (`>`, `<`, `-`, `<>`) |
| **Excalidraw** | 별도 라이브러리 사용 또는 직접 그리기 |
| **draw.io 추천** | 가장 정확한 까마귀발 지원, ERD 표기법 옵션 풍부 |

#### 한 줄 요약

> **카디널리티는 안쪽, 필수/선택은 바깥쪽**.
> 매핑 엔티티의 양쪽은 보통 둘 다 필수 (`─┤├`), 단 `MovieGenre.genre`처럼 nullable FK면 선택 (`─o├`)으로 그린다.

---

## 2. 시스템 아키텍처 — 단순화 버전

### 컴포넌트 박스 + 흐름

```
                  ┌────────────┐
                  │  Browser   │
                  └─────┬──────┘
                        │ HTTPS
                        ▼
                  ┌────────────┐
                  │    ALB     │
                  └─────┬──────┘
                        │
                        ▼
                  ┌────────────┐         ┌──────────┐
                  │ API Server │────────▶│   RDS    │
                  │  (Spring)  │         │  MySQL   │
                  └─────┬──────┘         └──────────┘
                        │
            ┌───────────┼───────────┐
            │           │           │
            ▼           ▼           ▼
       ┌────────┐  ┌────────┐  ┌──────────┐
       │   S3   │  │ Kafka  │  │ (콜백)   │
       │        │  │ Broker │  │  내부 API│
       └────┬───┘  └────┬───┘  └────▲─────┘
            │           │            │
            │           ▼            │
            │      ┌────────────┐    │
            │      │   Worker   │────┘
            │◀─────│ (ffmpeg)   │
            │      └────────────┘
            ▼
       HLS 결과물
```

### 컴포넌트 목록

| 컴포넌트 | 역할 |
|---|---|
| **Browser** | 사용자 클라이언트 |
| **ALB** | HTTPS 인바운드 진입점 |
| **API Server** | Spring Boot 애플리케이션 |
| **RDS (MySQL)** | 메인 데이터 저장소 |
| **S3** | presigned URL 직접 업로드, HLS 결과물 저장 |
| **Kafka Broker** | 인코딩 작업 큐 |
| **Worker** | Kafka consume → ffmpeg 인코딩 → 내부 callback |

### 흐름 라벨 (선 위에 적을 글자)

1. `Browser → ALB`: HTTPS
2. `ALB → API Server`: HTTP
3. `API Server → RDS`: JDBC
4. `API Server → S3`: presigned URL 발급
5. `Browser → S3`: 직접 업로드
6. `API Server → Kafka`: 인코딩 요청 발행
7. `Kafka → Worker`: consume
8. `Worker → S3`: 원본 download / 결과 upload
9. `Worker → API Server`: 내부 callback (`/internal/api/**`)

### 도식 그릴 때 배치 팁

- **상단**: Browser ─ ALB ─ API Server를 세로 일렬로 (요청 경로)
- **API Server 옆**: RDS는 오른쪽
- **하단 좌**: S3
- **하단 우**: Kafka ─ Worker
- callback 화살표는 Worker → API Server로 별도 색 권장 (단방향 점선 등)
