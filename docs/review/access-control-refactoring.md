# 접근 제어 리팩토링

## 문제 배경

HTTP 요청이 들어올 때 접근 제어는 두 단계로 나뉘어요.

```
[Filter] JwtAuthFilter  →  인증 (누구냐?)       토큰 만료 시 401
[Service] 소유권 검증   →  인가 (내 데이터냐?)  타인 데이터 접근 시 403
```

리팩토링 전에는 인가(소유권 검증)가 **컨트롤러와 서비스에 흩어져** 있었어요.

---

## 리팩토링 전 — 문제점

### 1. 소유권 체크 보일러플레이트가 컨트롤러마다 반복됨

`PersonController`의 write 엔드포인트마다 동일한 패턴이 반복됐어요.

```java
// createStoryboardProject, updateStoryboardProject, deleteStoryboardScene ...
// 모든 write 엔드포인트에 아래 코드가 그대로 복붙됨
Long currentPersonId = personReadService.findCurrentPersonId();
Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
if (!currentPersonId.equals(targetPersonId)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
personReadService.createStoryboardProject(currentPersonId, request);
```

### 2. 서비스 write 메서드가 소유권 검증 없이 personId를 신뢰

```java
// PersonReadService - personId가 현재 유저 것인지 검증 없음
public void updatePersonProfileImage(Long personId, String key) {
    Person person = personRepository.findById(personId)...;
    person.changeProfileImageUrl(key); // 누구나 personId만 알면 수정 가능
}
```

서비스 메서드는 컨트롤러가 올바른 personId를 넘겨준다고 신뢰했어요.
새 컨트롤러를 추가할 때 소유권 체크를 빠뜨리면 보안 구멍이 생기는 구조였어요.

### 3. 소유권 체크가 누락된 엔드포인트

`PersonController.uploadFilmography`는 소유권 체크 없이 바로 수정했어요.

```java
// 수정 전 - publicId가 현재 유저 것인지 확인 없음
Long personId = personReadService.findPersonIdByPublicId(publicId);
personReadService.updateFilmographyFile(personId, newKey); // 누구든 수정 가능
```

---

## 리팩토링 후 — 해결 방법

**서비스 write 메서드가 직접 현재 유저를 확인**하도록 변경했어요.

### PersonReadService — personId 파라미터 제거

```java
// 수정 전
public void updatePersonProfileImage(Long personId, String key) {
    Person person = personRepository.findById(personId)...;
    person.changeProfileImageUrl(key);
}

// 수정 후
public void updatePersonProfileImage(String key) {
    Long personId = findCurrentPersonId(); // 서비스가 직접 현재 유저 확인
    Person person = personRepository.findById(personId)...;
    person.changeProfileImageUrl(key);
}
```

동일하게 변경된 메서드 목록:

| 메서드 | 변경 내용 |
|---|---|
| `updatePersonProfileImage` | `Long personId` 파라미터 제거 |
| `addPersonGalleryImage` | `Long personId` 파라미터 제거 |
| `updateFilmographyFile` | `Long personId` 파라미터 제거 |
| `clearProfileImage` | `Long personId` 파라미터 제거 |
| `removeGalleryImage` | `Long personId` 파라미터 제거 |
| `reorderGallery` | `Long personId` 파라미터 제거 |
| `updateFilmographyPrivate` | `Long personId` 파라미터 제거 |
| `updateGalleryPrivate` | `Long personId` 파라미터 제거 |
| `updateGalleryItemPrivacy` | `Long personId` 파라미터 제거 |
| `createStoryboardProject` | `Long personId` 파라미터 제거 |
| `updateStoryboardProject` | `Long personId` 파라미터 제거 |
| `deleteStoryboardProject` | `Long personId` 파라미터 제거 |
| `createStoryboardScene` | `Long personId` 파라미터 제거 |
| `updateStoryboardScene` | `Long personId` 파라미터 제거 |
| `deleteStoryboardScene` | `Long personId` 파라미터 제거 |
| `reorderStoryboardScenes` | `Long personId` 파라미터 제거 |

### PersonController — 보일러플레이트 제거

```java
// 수정 전
Long currentPersonId = personReadService.findCurrentPersonId();
Long targetPersonId = personReadService.findPersonIdByPublicId(publicId);
if (!currentPersonId.equals(targetPersonId)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
StoryboardProject project = personReadService.createStoryboardProject(currentPersonId, request);

// 수정 후
StoryboardProject project = personReadService.createStoryboardProject(request);
```

### PersonFileController — 서비스 호출 단순화

```java
// 수정 전
Long personId = personReadService.findCurrentPersonId();
personReadService.updatePersonProfileImage(personId, newKey);

// 수정 후 (personId는 key 생성 목적으로만 사용)
Long personId = personReadService.findCurrentPersonId();
personReadService.updatePersonProfileImage(newKey);
```

---

## 수정 전 → 수정 후 비교

| | 수정 전 | 수정 후 |
|---|---|---|
| 소유권 검증 위치 | 컨트롤러 (일부 누락) | 서비스 (항상 보장) |
| write 메서드 시그니처 | `(Long personId, ...)` | `(...)` |
| 새 컨트롤러 추가 시 | 개발자가 체크 직접 추가 | 서비스 호출만 하면 자동 보장 |
| 누락 가능성 | 있음 | 없음 |

---

## 요청 흐름 요약

```
HTTP 요청
    ↓
[JwtAuthFilter]     → 토큰 유효한지 (인증: 누구냐?)
    ↓
[Controller]        → 요청/응답 변환만 담당
    ↓
[Service]           → findCurrentPersonId()로 소유권 확인 후 처리 (인가: 내 데이터냐?)
```
