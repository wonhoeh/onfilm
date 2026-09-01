# MySQL Unique·Nullable·FK Constraint 감사

- 감사일: 2026-09-01
- 대상 DB: `onfilm_api`
- 기준 Migration: `V1__create_initial_schema.sql`, `V2__seed_standard_genres.sql`
- 기준 실행 환경: MySQL 8.4.11, `utf8mb4_0900_ai_ci`
- 상태: 감사 완료, 변경 후보는 후속 Migration에서 검증 후 적용

## 목적

JPA 엔티티와 Flyway 스키마를 대조해 다음 질문에 답한다.

- 동시에 같은 요청이 들어와도 반드시 지켜져야 하는 중복 방지 규칙은 DB UNIQUE로 보호되는가?
- nullable 컬럼이 실제 도메인 상태를 표현하는가, 아니면 제약이 빠진 것인가?
- 부모 삭제 시 자식의 삭제·유지·차단 정책이 FK `ON DELETE`와 일치하는가?
- JPA 연관관계가 없는 ID 컬럼에 DB FK를 둘 것인가?
- Java의 문자열 비교 정책과 MySQL Collation이 같은 값을 동일하게 판단하는가?

이번 단계는 감사와 정책 결정만 수행한다. 적용된 V1을 수정하지 않으며 실제 변경은 새 Migration과 거부 테스트를 같은 커밋에 추가한다.

## 전체 현황

V1의 명시적 제약조건은 다음과 같다.

| 항목 | 개수 | 비고 |
|---|---:|---|
| 테이블 | 19 | API 소유 테이블과 값 컬렉션 |
| Primary Key | 18 | `movie_likes`만 PK 없음 |
| PK 외 Unique Constraint | 14 | 사용자 식별자, 참여 관계, 정규화 값, 멱등성 키 등 |
| Check Constraint | 1 | `movie_person_role`의 역할별 필드 조합 |
| Foreign Key | 14 | API 논리 DB 내부 관계만 존재 |
| `ON DELETE CASCADE` | 12 | 부모와 생명주기를 공유하는 소유 자식 |
| `ON DELETE RESTRICT` | 2 | `users → person`, `movie_genre → genre` |
| nullable `sort_order` | 6 | 저장 후에는 값이 필요하므로 보강 후보 |

핵심 Aggregate의 FK와 중복 방지는 대체로 의도와 일치한다. 우선 보완할 항목은 Refresh Token의 정확 비교와 사용자 생명주기, OrderColumn의 nullable, Gallery 중복, 그리고 제약 구조가 없는 `movie_likes`다.

## 검증 책임의 경계

같은 규칙을 여러 계층에서 확인하더라도 목적은 다르다.

| 계층 | 책임 | 예시 |
|---|---|---|
| Request DTO | 요청 형식과 빠른 4xx 응답 | 필수 목록, 문자열 길이, null 원소 |
| Entity | 생성·변경 경로 전체의 도메인 불변식 | 역할별 입력 조합, 음수 정렬 순서 거부 |
| Service | 유스케이스와 권한·존재·소유권 | 현재 사용자가 해당 Movie를 수정할 수 있는가 |
| Database | 동시 요청과 우회 쓰기에도 깨지면 안 되는 저장 불변식 | UNIQUE, NOT NULL, CHECK, FK |

DB Constraint 위반은 정상적인 사용자 검증 수단이 아니라 최종 방어선이다. 서비스는 의미 있는 도메인 오류를 먼저 반환하되, 사전 중복 검사 직후 발생하는 경쟁 조건은 DB UNIQUE 위반을 도메인 예외로 변환한다.

## 테이블별 감사 결과

### 사용자·인증

| 테이블 | 현재 핵심 제약 | nullable 정책 | FK·삭제 정책 | 판정 |
|---|---|---|---|---|
| `person` | `public_id` UNIQUE | 생년월일·출생지·소개·파일 key는 선택 정보 | 독립 Person 허용 | 유지 |
| `users` | `person_id`, `email`, `username_normalized` UNIQUE | avatar key만 선택 | Person 삭제는 RESTRICT | 유지 |
| `refresh_tokens` | `token_hash` UNIQUE | 폐기·마지막 사용 시각은 상태에 따라 선택 | `user_id` FK 없음 | 보강 필요 |

`users.person_id`의 UNIQUE와 NOT NULL은 User가 정확히 하나의 Person을 가져야 한다는 규칙을 보장한다. 반대 방향은 필수가 아니다. 아직 가입하지 않은 외부 참여자를 Person으로 표현할 수 있으므로 모든 Person에 User가 존재하도록 강제하지 않는다.

`fk_users_person ON DELETE RESTRICT`는 User가 참조 중인 Person의 직접 삭제를 차단한다. 회원 탈퇴는 애플리케이션이 User를 먼저 삭제하고, JPA가 생명주기를 공유하는 Person을 삭제한다. Person 삭제는 SNS·태그·갤러리·스토리보드와 Movie 참여 관계를 제거하지만 Movie 자체는 삭제하지 않는다.

Refresh Token은 JPA 연관관계를 두지 않는 것이 적절하지만 DB FK는 별개의 결정이다. 현재 token만 남으면 `rotate()`가 User 존재를 다시 조회하지 않고 해당 `userId`로 새 토큰을 만들 수 있다. 따라서 다음 Migration에서는 `refresh_tokens(user_id) → users(user_id) ON DELETE CASCADE`를 추가한다. ORM 결합은 늘리지 않으면서 다음을 보장한다.

- 존재하지 않는 User의 Refresh Token 발급 거부
- 회원 탈퇴 시 모든 인증 세션의 DB 수준 제거
- 애플리케이션 탈퇴 로직 누락 시에도 credential 잔존 방지

### 영화·참여·장르

| 테이블 | 현재 핵심 제약 | nullable 정책 | FK·삭제 정책 | 판정 |
|---|---|---|---|---|
| `movie` | PK | movie·thumbnail key는 미등록·삭제 상태에서 null | 독립 Aggregate | 유지, CHECK 후보 |
| `movie_likes` | FK만 존재, PK·UNIQUE 없음 | `likes`도 nullable | Movie 삭제 시 CASCADE | 모델 결정 필요 |
| `movie_person` | `(movie_id, person_id)` UNIQUE | 필수 컬럼만 존재 | Movie·Person 어느 쪽 삭제에도 CASCADE | 유지 |
| `movie_person_role` | `(movie_person_id, role)` UNIQUE, 역할 CHECK | 배우 배역명은 선택, 제작진 배우 필드는 null | 참여 삭제 시 CASCADE | 유지, 순서 보강 |
| `movie_genre` | `(movie_id, normalized_text)` UNIQUE | `genre_id`는 사용자 장르일 때 null | Movie CASCADE, 표준 Genre RESTRICT | 유지 |
| `trailer` | `(movie_id, storage_key)` UNIQUE | storage key는 필수, 순서만 nullable | Movie 삭제 시 CASCADE | 순서 보강 |
| `genre` | `normalized` UNIQUE | 없음 | 참조 중인 Genre 삭제 RESTRICT | 유지 |

`MoviePerson`과 `MoviePersonRole` 분리는 현재 제약과 일치한다.

- 한 작품에서 같은 Person의 참여는 한 행만 허용한다.
- 참여 한 건에는 ACTOR·DIRECTOR·WRITER가 각각 최대 한 번 존재한다.
- ACTOR는 `cast_type`이 필수이고 DIRECTOR·WRITER에는 배우 전용 필드를 허용하지 않는다.
- Movie 삭제는 그 Movie의 참여 이력을 제거한다.
- Person 삭제는 그 Person의 참여 이력만 제거하고 Movie와 다른 참여자의 이력은 유지한다.

`movie_genre.genre_id`의 null은 결함이 아니다. 표준 Genre면 FK를 사용하고 사용자 입력 Genre면 표시·정규화 문자열만 저장하는 의도적인 합타입이다. 표준 Genre는 참조 중에 물리 삭제하지 않고 `is_active`로 비활성화한다.

`movie_likes`는 현재 `List<String>` 값 컬렉션이지만 추가·삭제 유스케이스가 없고 행 식별자와 중복 방지 규칙도 없다. 단순히 PK를 추가하면 “문자열 하나가 좋아요 한 건인가, User의 좋아요인가”라는 모델 문제를 숨긴다. 기능을 시작할 때 `MovieLike(movie_id, user_id, created_at)` 관계 엔티티로 재설계하거나 사용하지 않으면 필드와 테이블을 제거한다. 이번 Constraint Migration에는 포함하지 않는다.

### 프로필·스토리보드

| 테이블 | 현재 핵심 제약 | nullable 정책 | FK·삭제 정책 | 판정 |
|---|---|---|---|---|
| `person_sns` | `(person_id, url)` UNIQUE | 없음 | Person 삭제 시 CASCADE | 유지 |
| `profile_tag` | `(person_id, normalized)` UNIQUE | `sort_order`만 nullable | Person 삭제 시 CASCADE | 순서 보강 |
| `person_gallery` | `(sort_order, person_id)` PK | 없음 | Person 삭제 시 CASCADE | 이미지 중복 보강 |
| `storyboard_project` | PK | `sort_order`만 nullable | Person 삭제 시 CASCADE | 순서 보강 |
| `storyboard_scene` | PK | 제목·스크립트는 선택, 순서 nullable | Project 삭제 시 CASCADE | 순서 보강 |
| `storyboard_card` | PK | 빈 Card를 허용해 image key 선택, 순서 nullable | Scene 삭제 시 CASCADE | 순서 보강 |

Scene 제목과 Card image key의 null은 편집 중인 빈 장면·빈 카드 상태를 지원하는 정책이므로 유지한다. 반면 JPA `@OrderColumn`이 관리하는 다음 6개 컬럼은 저장된 행에서 null일 이유가 없다.

- `movie_person_role.sort_order`
- `trailer.sort_order`
- `profile_tag.sort_order`
- `storyboard_project.sort_order`
- `storyboard_scene.sort_order`
- `storyboard_card.sort_order`

통합 테스트에서 Hibernate가 실제 MySQL에 0부터 시작하는 값을 저장하고 삭제 후 순서를 압축하는 동작을 확인했다. 후속 Migration에서 JPA `@OrderColumn(nullable = false)`와 DB NOT NULL을 함께 적용하고 음수 방지 CHECK를 추가한다.

부모별 `UNIQUE(parent_id, sort_order)`는 추가하지 않는다. JPA가 목록 재정렬 과정에서 여러 UPDATE를 순차 실행할 때 최종 상태는 유일해도 중간 상태에서 기존 순서와 충돌할 수 있기 때문이다. 순서의 완전한 순열은 Aggregate 메서드와 Repository 통합 테스트가 보장하고, DB는 NOT NULL·음수 방지만 담당한다.

Gallery는 Entity가 같은 image key의 중복을 거부하지만 DB에는 `(person_id, image_key)` UNIQUE가 없다. 동시 요청의 최종 방어선으로 `uk_person_gallery_image_key`를 추가한다. 현재 PK 순서 `(sort_order, person_id)`는 부모 중심 키인 `(person_id, sort_order)`보다 의미가 약하고 Person 조회용 FK 인덱스를 재사용하지 못할 수 있으므로 Index 감사 단계에서 PK 순서 변경을 함께 검토한다.

### 미디어 작업·Outbox

| 테이블 | 현재 핵심 제약 | nullable 정책 | FK·삭제 정책 | 판정 |
|---|---|---|---|---|
| `media_upload_requests` | UUID PK, `@Version` | 완료 전 `job_id`, `completed_at` null | User·Movie·Job FK 없음 | 약한 관계 유지, 상태 CHECK 후보 |
| `media_encode_jobs` | `request_id` UNIQUE, UUID PK, `@Version` | 상태별 시간·실패 정보 선택 | User·Movie·UploadRequest FK 없음 | 약한 관계 유지, 상태 CHECK 후보 |
| `media_encode_outbox` | `job_id` UNIQUE, UUID PK, `@Version` | lease·발행·실패 정보는 상태별 선택 | Job FK 없음 | 약한 관계 유지, 상태 CHECK 후보 |

이 세 테이블의 ID 참조는 작업 시점의 스냅샷과 장애 추적을 위한 약한 관계다. Movie나 User가 삭제되어도 Job 이력을 유지해야 하고, DEAD Outbox는 자동 삭제하지 않으며, UploadRequest·Job·Outbox의 보존 기간도 서로 다르다. 따라서 다음 FK는 추가하지 않는다.

- `media_upload_requests.requested_by_user_id → users`
- `media_upload_requests.movie_id → movie`
- `media_upload_requests.job_id → media_encode_jobs`
- `media_encode_jobs.request_id → media_upload_requests`
- `media_encode_jobs.movie_id → movie`
- `media_encode_jobs.requested_by_user_id → users`
- `media_encode_outbox.job_id → media_encode_jobs`

Job·Outbox·UploadRequest 완료는 애플리케이션의 한 트랜잭션으로 원자성을 보장하고, `request_id`와 `job_id` UNIQUE가 중복 생성을 차단한다. `media_upload_requests.job_id`에도 non-null 값의 중복을 막는 UNIQUE를 추가할 수 있지만 양쪽 ID의 상호 일치까지 보장하지는 못하므로 상태 CHECK와 함께 별도 검증 후 적용한다.

## FK 삭제 정책 감사

| FK | ON DELETE | 삭제 결과 | 판정 |
|---|---|---|---|
| `users.person_id → person.id` | RESTRICT | User가 있는 Person 직접 삭제 차단 | 유지 |
| `movie_likes.movie_movie_id → movie.movie_id` | CASCADE | Movie의 값 컬렉션 제거 | 모델 결정 전 유지 |
| `movie_person.movie_id → movie.movie_id` | CASCADE | Movie 삭제 시 전체 참여 제거 | 유지 |
| `movie_person.person_id → person.id` | CASCADE | Person 삭제 시 해당 참여만 제거 | 유지 |
| `movie_person_role.movie_person_id → movie_person.id` | CASCADE | 참여 삭제 시 역할 제거 | 유지 |
| `movie_genre.movie_id → movie.movie_id` | CASCADE | Movie 삭제 시 장르 연결 제거 | 유지 |
| `movie_genre.genre_id → genre.id` | RESTRICT | 사용 중인 표준 Genre 삭제 차단 | 유지 |
| `trailer.movie_id → movie.movie_id` | CASCADE | Movie 삭제 시 Trailer 제거 | 유지 |
| `person_sns.person_id → person.id` | CASCADE | Person 삭제 시 SNS 제거 | 유지 |
| `profile_tag.person_id → person.id` | CASCADE | Person 삭제 시 태그 제거 | 유지 |
| `person_gallery.person_id → person.id` | CASCADE | Person 삭제 시 갤러리 제거 | 유지 |
| `storyboard_project.person_id → person.id` | CASCADE | Person 삭제 시 프로젝트 제거 | 유지 |
| `storyboard_scene.project_id → storyboard_project.id` | CASCADE | Project 삭제 시 Scene 제거 | 유지 |
| `storyboard_card.scene_id → storyboard_scene.id` | CASCADE | Scene 삭제 시 Card 제거 | 유지 |

JPA cascade와 DB `ON DELETE`는 역할이 다르다. JPA cascade는 애플리케이션에서 Aggregate를 변경할 때 영속성 작업을 전파하고, DB FK는 native SQL·운영 도구·동시 요청을 포함한 모든 쓰기의 참조 무결성을 보장한다. 두 정책은 같은 생명주기 결정을 표현하도록 유지한다.

## 문자열 Collation 감사

DB 기본 `utf8mb4_0900_ai_ci`에서 `ci`는 대소문자를, `ai`는 악센트를 구분하지 않는 비교 정책이다. 이는 이메일·ASCII로 제한된 `username_normalized`에는 의도와 맞지만 모든 식별자에 적합하지는 않다.

### 즉시 보강 대상

`refresh_tokens.token_hash`는 SHA-256 결과의 URL-safe Base64 문자열이며 대소문자가 서로 다른 값이다. Java는 정확 비교하지만 현재 DB UNIQUE와 `WHERE token_hash = ?`는 기본 Collation의 영향을 받는다. `VARCHAR(43) CHARACTER SET ascii COLLATE ascii_bin`으로 변경해 정확 비교와 UNIQUE 의미를 일치시킨다.

### 후속 검토 대상

- `genre.normalized`, `movie_genre.normalized_text`, `profile_tag.normalized`: Java 정규화 결과와 DB의 accent-insensitive 비교가 같은지 샘플 데이터로 검증한다.
- storage key와 URL: S3 key와 URL path는 대소문자 구분이 가능하다. 현재 생성 key가 소문자 UUID 중심이라는 전제가 깨지면 binary Collation을 적용한다.
- UUID: 도메인이 canonical lowercase UUID만 허용하므로 현재 Collation 차이가 실질적인 중복을 만들지 않는다.

## 후속 Constraint Migration 범위

후속 단계에서는 아래 우선순위에 따라 새 Migration과 MySQL 거부 테스트를 작성한다. 변경 범위와 rollback 영향을 분리하기 위해 P0 인증 제약과 P1 Aggregate 제약은 서로 다른 Migration으로 관리한다.

### P0: 인증 무결성

1. V3에서 `refresh_tokens.token_hash`를 `ascii_bin` 정확 비교로 변경한다.
2. `fk_refresh_tokens_user`를 `ON DELETE CASCADE`로 추가한다.
3. 대소문자만 다른 token hash가 별개 값이며, 없는 User의 token이 거부되고 User 삭제 시 token이 제거되는지 검증한다.

### P1: 순서와 Aggregate 불변식

1. V4에서 6개 OrderColumn을 NOT NULL로 변경하고 JPA 매핑도 일치시킨다.
2. `movie_person.sort_order`, 6개 OrderColumn과 `person_gallery.sort_order`에 0 이상 CHECK를 추가한다.
3. `uk_person_gallery_image_key(person_id, image_key)`를 추가한다.
4. `ck_movie_runtime`으로 1~1000을, `ck_movie_release_year_min`으로 1900 이상을 보장한다. 현재 연도 + 1 상한은 시간 의존적이므로 Entity 검증에 유지한다.

### P2: 상태 조합

다음 제약은 상태 전이 중 Hibernate SQL 순서와 유지보수 작업을 먼저 검증한 뒤 별도 Migration으로 적용한다.

- UploadRequest의 `expires_at > issued_at`
- COMPLETED일 때 `job_id`, `completed_at` 필수
- Job의 DONE·FAILED 상태별 완료·실패 컬럼 조합
- Outbox의 attempts 범위와 PUBLISHING·PUBLISHED 상태별 시각 컬럼 조합
- `media_upload_requests.job_id`의 non-null UNIQUE

복잡한 상태 CHECK를 한 번에 추가하면 합법적인 전이의 중간 SQL이나 장애 복구를 차단할 수 있으므로 P0·P1과 분리한다.

## 의도적으로 적용하지 않는 제약

- API DB와 Worker DB 사이 FK·JOIN·트랜잭션
- Movie·User와 미디어 작업 이력 사이 FK
- 부모별 `UNIQUE(parent_id, sort_order)`
- 모든 Person이 User를 가져야 한다는 역방향 필수 제약
- 제목·Person 이름의 UNIQUE
- `movie_likes`의 임시 PK 또는 UNIQUE

## 검증 기준

후속 Migration마다 다음을 충족해야 한다.

1. 빈 MySQL에 V1부터 모든 Migration 적용
2. Hibernate `ddl-auto: validate` 통과
3. 새 NOT NULL·UNIQUE·CHECK·FK마다 정상 저장과 거부 테스트 추가
4. FK 삭제 정책마다 부모 삭제 결과 확인
5. 기존 Repository·트랜잭션 통합 테스트 전체 통과
6. `./gradlew test`, `./gradlew integrationTest`, 최종 `./gradlew check` 통과

## 참고 자료

- [Movie 참여와 역할 모델링 정책](../../decisions/movie-person-role-modeling-policy.md)
- [FK 컬럼만 가진 약한 관계 설계](../modeling/fk-컬럼만-가진-약한-관계-설계.md)
- [API·Worker DB 소유권과 Flyway 정책](../../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md)
- [MySQL 트랜잭션과 잠금 통합 테스트](../../testing/mysql-transaction-and-locking.md)
- [MySQL 8.4 Character Set·Collation](https://dev.mysql.com/doc/refman/8.4/en/charset-general.html)
- [MySQL 8.4 Foreign Key Constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)
