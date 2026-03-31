# onfilm 프로젝트 개발 규칙

## 패키지 구조

도메인 단위로 패키지를 구성한다.

```
com.onfilm.domain
├── auth
│   ├── config        - SecurityConfig, AuthProperties
│   ├── security      - JwtAuthFilter, JwtProvider, CsrfProtectionFilter 등
│   ├── infrastructure - AuthCookieFactory
│   ├── controller
│   ├── service
│   └── dto
├── movie
│   ├── controller
│   ├── service
│   ├── entity
│   ├── repository
│   └── dto
├── user
├── token
├── kafka
└── common
    ├── error
    └── util
```

---

## 레이어별 책임

### Controller
- 요청 파싱 / 응답 변환만 담당
- 비즈니스 로직, 접근 제어 로직을 컨트롤러에 작성하지 않는다

### Service
- 비즈니스 로직 처리
- 접근 제어(소유권 검증) 담당

### Repository
- DB 접근만 담당
- 복잡한 조회는 @Query로 JPQL 직접 작성

---

## 접근 제어 규칙

### 원칙
- 인증(누구냐) → JwtAuthFilter에서 처리
- 인가(내 데이터냐) → Service 레이어에서만 처리
- 컨트롤러에서 소유권 검증 코드를 작성하지 않는다

### write 서비스 메서드 시그니처
personId를 파라미터로 받지 않고 내부에서 `findCurrentPersonId()`로 현재 유저를 직접 확인한다.

```java
// ❌ 금지 - personId를 파라미터로 받아 신뢰
public void updateProfile(Long personId, String key) {
    Person person = personRepository.findById(personId)...;
}

// ✅ 올바른 방식 - 서비스가 직접 현재 유저 확인
public void updateProfile(String key) {
    Long personId = findCurrentPersonId();
    Person person = personRepository.findById(personId)...;
}
```

### 컨트롤러에서 금지하는 패턴
```java
// ❌ 컨트롤러에서 소유권 체크 금지
Long currentPersonId = personReadService.findCurrentPersonId();
Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
if (!currentPersonId.equals(targetPersonId)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

---

## N+1 쿼리 방지 규칙

### 원칙
- 컬렉션 연관관계는 기본 LAZY 로딩을 유지한다
- 연관 데이터가 필요한 조회는 fetch join 또는 @EntityGraph를 사용한다

### 선택 기준
- 단순 연관 데이터 조회 → `@EntityGraph`
- DISTINCT, WHERE 커스텀, 정렬 등 쿼리 제어가 필요한 경우 → fetch join

```java
// fetch join 예시 - DISTINCT가 필요한 1:N:N 구조
@Query("""
    SELECT DISTINCT p FROM Person p
    LEFT JOIN FETCH p.storyboardProjects sp
    LEFT JOIN FETCH sp.scenes
    WHERE p.publicId = :publicId
""")
Optional<Person> findByPublicIdWithStoryboards(@Param("publicId") String publicId);
```

### 새 조회 메서드 추가 시
- 반복문 안에서 컬렉션에 접근하는 경우 반드시 fetch join 여부를 검토한다
- 기존 `findByPublicId`를 수정하지 말고 용도에 맞는 메서드를 별도로 추가한다

---

## 예외 처리 규칙

- 도메인 예외는 `common/error/exception` 패키지에 정의한다
- `GlobalExceptionHandler`에서 HTTP 상태코드로 매핑한다
- 상태 전이 불가 → 409 Conflict
- 소유권 없음 → 403 Forbidden
- 리소스 없음 → 404 Not Found

---

## Kafka / 비동기 처리 규칙

### 멱등성
- 컨슈머는 중복 메시지를 받아도 안전하게 처리되어야 한다
- 터미널 상태(DONE, FAILED)에서 중복 콜백이 오면 예외를 던지지 않고 무시한다

```java
// ✅ 터미널 상태 중복 콜백 처리
public void markDone(Instant completedAt) {
    if (this.status == MediaEncodeJobStatus.DONE) {
        return; // 중복 콜백 무시
    }
    if (this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION"); // 409
    }
    ...
}
```

### 상태 전이
- 유효하지 않은 상태 전이는 `IllegalStateException`을 던진다
- `GlobalExceptionHandler`에서 409 Conflict로 매핑한다

---

## JPA 규칙

- 연관관계 컬렉션은 기본 `FetchType.LAZY`를 사용한다
- `FetchType.EAGER`를 어노테이션에 직접 설정하지 않는다
- 필요한 경우에만 fetch join으로 명시적으로 로딩한다
