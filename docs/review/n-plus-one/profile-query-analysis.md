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

## 수정 우선순위

| 문제 | 심각도 | 수정 방법 |
|---|---|---|
| 스토리보드 N+1 | 높음 | fetch join으로 scenes까지 한 번에 조회 |
| 프로필 lazy load | 중간 | `@EntityGraph` 또는 fetch join |
| person 중복 조회 | 낮음 | 당장 크게 문제 없음 |

---

## 수정 방향 (스토리보드 N+1)

`PersonRepository`에 fetch join 쿼리 추가

```java
@Query("""
    SELECT DISTINCT p FROM Person p
    LEFT JOIN FETCH p.storyboardProjects sp
    LEFT JOIN FETCH sp.scenes
    WHERE p.publicId = :publicId
""")
Optional<Person> findByPublicIdWithStoryboards(@Param("publicId") String publicId);
```

이렇게 하면 person + projects + scenes를 쿼리 1개로 조회할 수 있어요.

## 수정 방향 (프로필 lazy load)

```java
@Query("""
    SELECT DISTINCT p FROM Person p
    LEFT JOIN FETCH p.snsList
    LEFT JOIN FETCH p.profileTags
    WHERE p.publicId = :publicId
""")
Optional<Person> findByPublicIdWithProfile(@Param("publicId") String publicId);
```
