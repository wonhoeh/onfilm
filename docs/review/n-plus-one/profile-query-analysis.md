# 프로필 조회 N+1 분석

## N+1 문제란?

데이터 1건을 조회한 뒤, 연관된 데이터를 N건 추가로 조회하는 상황이에요.

```
SELECT * FROM person WHERE public_id = ?          → 1번
SELECT * FROM storyboard_scene WHERE project_id = 1  → N번 (프로젝트 수만큼)
SELECT * FROM storyboard_scene WHERE project_id = 2
SELECT * FROM storyboard_scene WHERE project_id = 3
...
```

쿼리가 1 + N개 나간다고 해서 N+1 문제라고 불러요.

---

## 발견된 문제

### 1. findStoryboardProjectsByPublicId — 진짜 N+1 (심각도 높음)

**파일:** `PersonReadService.java`

```java
Person person = personRepository.findByPublicId(publicId); // 쿼리 1: person 조회

person.getStoryboardProjects()  // 쿼리 2: 프로젝트 목록 전체 조회

for (StoryboardProject project : person.getStoryboardProjects()) {
    project.getScenes().size()  // 쿼리 3, 4, 5... 프로젝트 수만큼 반복 발생!
}
```

**실제 발생 쿼리 수:**

| 프로젝트 수 | 발생 쿼리 수 |
|---|---|
| 1개 | 3개 |
| 5개 | 7개 |
| 10개 | 12개 |

프로젝트가 많아질수록 쿼리가 선형으로 늘어나요.

**원인:** `StoryboardProject`의 `scenes`가 LAZY 로딩이라 반복문 안에서 접근할 때마다 쿼리가 나가요.

---

### 2. findProfileByPublicId — lazy 컬렉션 추가 조회 (심각도 중간)

**파일:** `PersonReadService.java`, `ProfileResponse.java`

```java
Person person = personRepository.findByPublicId(publicId); // 쿼리 1: person 조회

// ProfileResponse.from(person, publicUrl) 내부에서
person.getSnsList()       // 쿼리 2: SELECT * FROM person_sns WHERE person_id = ?
person.getProfileTags()   // 쿼리 3: SELECT * FROM profile_tag WHERE person_id = ?
```

단건 조회라 N+1은 아니지만, fetch join 하나로 1개의 쿼리로 줄일 수 있어요.

---

### 3. 같은 person을 여러 번 조회 (심각도 낮음)

아래 메서드들이 각각 독립적으로 `findByPublicId`를 호출해요.

```java
isFilmographyPrivate(publicId)    // SELECT * FROM person WHERE public_id = ?
isGalleryPrivate(publicId)        // SELECT * FROM person WHERE public_id = ?
findGalleryItemsByPublicId(publicId) // SELECT * FROM person WHERE public_id = ?
```

프로필 페이지에서 이 메서드들을 연달아 호출하면 같은 person을 DB에서 여러 번 읽어요.
1차 캐시(영속성 컨텍스트)가 같은 트랜잭션 안이면 막아주지만, 각 API 호출이 별도 트랜잭션이면 매번 DB에 날아가요.

---

## 실측 결과 (수정 전)

**환경:** 로컬 H2 / 스토리보드 프로젝트 10개, 프로젝트당 씬 3개

**API:** `GET /api/people/test-public-id-001/storyboard/projects`

**p6spy 측정 쿼리 목록 (exec-9 스레드 기준):**

```
1.  SELECT person WHERE public_id = ?            ← findPersonIdByPublicId
2.  SELECT person WHERE public_id = ?            ← findStoryboardProjectsByPublicId (중복)
3.  SELECT storyboard_project WHERE person_id=1
4.  SELECT storyboard_scene WHERE project_id=1   ← N+1 시작
5.  SELECT storyboard_scene WHERE project_id=2
6.  SELECT storyboard_scene WHERE project_id=3
7.  SELECT storyboard_scene WHERE project_id=4
8.  SELECT storyboard_scene WHERE project_id=5
9.  SELECT storyboard_scene WHERE project_id=6
10. SELECT storyboard_scene WHERE project_id=7
11. SELECT storyboard_scene WHERE project_id=8
12. SELECT storyboard_scene WHERE project_id=9
13. SELECT storyboard_scene WHERE project_id=10
```

**총 13쿼리** (person 중복 조회 2회 + 프로젝트 목록 1회 + scenes N+1 10회)

프로젝트 수가 늘수록 쿼리가 선형으로 증가 → fetch join 적용 시 1쿼리로 줄일 수 있음

---

## 수정 우선순위

| 문제 | 심각도 | 수정 방법 |
|---|---|---|
| 스토리보드 N+1 | 높음 | fetch join으로 scenes까지 한 번에 조회 |
| 프로필 lazy load | 중간 | `@EntityGraph` 또는 fetch join |
| person 중복 조회 | 낮음 | 당장 크게 문제 없음 |

---

---

## 수정 내용 (스토리보드 N+1)

### 변경 파일 1: PersonRepository.java

```java
// 수정 전 - 기본 조회 (scenes LAZY 로딩)
Optional<Person> findByPublicId(String publicId);

// 수정 후 - fetch join으로 projects + scenes 한 번에 조회
@Query("""
    SELECT DISTINCT p FROM Person p
    LEFT JOIN FETCH p.storyboardProjects sp
    LEFT JOIN FETCH sp.scenes
    WHERE p.publicId = :publicId
""")
Optional<Person> findByPublicIdWithStoryboards(@Param("publicId") String publicId);
```

#### 왜 @EntityGraph가 아닌 fetch join을 선택했나?

N+1 해결 방법은 두 가지가 있어요.

| | fetch join | @EntityGraph |
|---|---|---|
| 방식 | `@Query`에 JPQL 직접 작성 | 어노테이션으로 경로만 지정 |
| 가독성 | 쿼리가 길어질수록 복잡 | 간결 |
| 유연성 | WHERE절, DISTINCT 등 자유롭게 조작 가능 | 단순 조회에 적합 |

```java
// @EntityGraph 방식 (단순 조회에 적합)
@EntityGraph(attributePaths = {"storyboardProjects", "storyboardProjects.scenes"})
Optional<Person> findByPublicId(String publicId);
```

이번 케이스에서 fetch join을 선택한 이유는 `DISTINCT`가 필요했기 때문이에요.

person → projects → scenes 구조에서 LEFT JOIN을 하면 행이 곱해져서 person이 scenes 수만큼 중복으로 나와요.
`@EntityGraph`는 이 중복을 제어하기 까다롭고, fetch join은 JPQL에 `DISTINCT`를 직접 명시할 수 있어요.

**선택 기준 요약**
- 단순히 연관 데이터만 같이 가져오면 될 때 → `@EntityGraph`
- DISTINCT, WHERE 커스텀, 정렬 등 쿼리를 직접 제어해야 할 때 → fetch join

### 변경 파일 2: PersonReadService.java

```java
// 수정 전
Person person = personRepository.findByPublicId(publicId)
        .orElseThrow(() -> new PersonNotFoundException(publicId));

// 수정 후
Person person = personRepository.findByPublicIdWithStoryboards(publicId)
        .orElseThrow(() -> new PersonNotFoundException(publicId));
```

---

## 수정 전 → 수정 후 비교

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| 총 쿼리 수 (프로젝트 10개 기준) | 13개 | **1개** |
| scenes 조회 방식 | 프로젝트별 개별 쿼리 (LAZY) | fetch join으로 한 번에 |
| 프로젝트 100개일 경우 | 103개 | **1개** |
| 실측 환경 | 로컬 H2, 프로젝트 10개 / 씬 30개 | 동일 환경 |

### 수정 전 실행 쿼리
```sql
SELECT person WHERE public_id = ?              -- 1번
SELECT person WHERE public_id = ?              -- 2번 (중복)
SELECT storyboard_project WHERE person_id = 1  -- 3번
SELECT storyboard_scene WHERE project_id = 1   -- 4번  ┐
SELECT storyboard_scene WHERE project_id = 2   -- 5번  │
SELECT storyboard_scene WHERE project_id = 3   -- 6번  │
...                                                     │ N+1
SELECT storyboard_scene WHERE project_id = 10  -- 13번 ┘
```

### 수정 후 실행 쿼리
```sql
SELECT DISTINCT p FROM Person p
LEFT JOIN FETCH p.storyboardProjects sp
LEFT JOIN FETCH sp.scenes
WHERE p.publicId = ?                           -- 1번으로 끝
```

---

## 향후 개선 후보 (프로필 lazy load)

`findProfileByPublicId`도 fetch join 적용 시 3쿼리 → 1쿼리로 줄일 수 있음

```java
@Query("""
    SELECT DISTINCT p FROM Person p
    LEFT JOIN FETCH p.snsList
    LEFT JOIN FETCH p.profileTags
    WHERE p.publicId = :publicId
""")
Optional<Person> findByPublicIdWithProfile(@Param("publicId") String publicId);
```
