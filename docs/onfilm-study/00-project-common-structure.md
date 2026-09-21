# 0. 프로젝트 공통 구조

코드에 들어가기 전에 OnFilm 전체의 큰 그림과 모든 기능에 공통으로 사용되는 구조를 이해한다.

## 0-1. OnFilm은 어떤 서비스인가?

OnFilm은 배우가 흩어져 있는 출연 영상을 한곳에서 관리하고, 자신의 프로필과 필모그래피를 포트폴리오 형태로 제공할 수 있게 만든 서비스다.

주요 사용자는 다음과 같은 일을 할 수 있다.

- 회원가입하고 로그인
- 배우 프로필 작성
- 출연 작품과 역할 등록
- 프로필 사진과 갤러리 관리
- 스토리보드 작성
- 원본 영상 업로드
- 변환된 영상을 포트폴리오에서 재생
- 자신의 프로필 페이지를 다른 사람에게 공유

즉, OnFilm은 단순한 동영상 재생 서비스가 아니다.

> 배우가 프로필·출연 작품·영상을 관리하고, 이를 하나의 포트폴리오로 보여주는 서비스다.

---

## 0-2. 왜 서버가 여러 개 필요한가?

일반적인 기능은 API 서버가 처리한다.

```text
프로필 조회
회원가입
로그인
필모그래피 등록
스토리보드 수정
```

하지만 영상 인코딩은 CPU를 많이 사용하고 완료될 때까지 시간도 오래 걸린다.

API 서버가 영상 인코딩까지 직접 처리하면 다음 문제가 발생한다.

```text
사용자가 영상 업로드
       ↓
API 서버가 영상 인코딩 시작
       ↓
CPU 사용량 증가
       ↓
프로필 조회나 로그인 같은 일반 요청도 느려짐
```

그래서 역할을 분리했다.

```text
API 서버
- 사용자 요청 접수
- 권한 확인
- DB 조회와 저장
- 작업 상태 제공

Worker
- 원본 영상 다운로드
- FFmpeg로 영상 변환
- 변환 결과 업로드
- API에 완료 결과 통지
```

> 빠르게 처리해야 하는 사용자 요청은 API가 담당하고, 오래 걸리고 CPU를 많이 사용하는 영상 변환은 Worker가 담당한다.

---

## 0-3. 전체 구성요소

### 브라우저

사용자가 실제로 이용하는 화면이다. API 서버에 로그인, 프로필 조회, 작품 등록 등의 HTTP 요청을 보낸다.

### ALB

외부에서 들어온 요청을 API 서버로 전달하는 입구다.

```text
사용자 → ALB → API 서버
```

### API 서버

OnFilm의 주요 업무 규칙을 처리한다.

- 로그인한 사용자인지 확인
- 해당 프로필을 수정할 권한이 있는지 확인
- 입력값이 올바른지 검사
- 프로필과 작품 정보를 DB에 저장
- 영상 변환 작업 생성
- 작업 진행 상태 응답

### MySQL/RDS

서비스의 구조화된 정보를 저장한다.

```text
사용자 계정
배우 프로필
영화
필모그래피
스토리보드
영상 작업 상태
메시지 전송 기록
```

영상 파일 자체를 저장하는 곳은 아니다.

### S3

용량이 큰 실제 파일을 저장한다.

```text
프로필 이미지
갤러리 이미지
원본 영상
변환된 HLS 영상
썸네일
```

DB에는 보통 파일 자체가 아니라 S3에서 파일을 찾을 수 있는 `storageKey`를 저장한다.

> Storage Key는 S3나 파일 저장소에서 특정 파일을 찾기 위해 DB에 저장하는 파일 경로다.

```text
profiles/10/profile.jpg
```

### Kafka

API가 Worker에 영상 변환 작업을 전달하는 통로다.

```text
API → Kafka → Worker
```

API는 Kafka에 “이 영상을 변환해 달라”는 메시지를 전달하고, 곧바로 사용자 요청 처리를 끝낼 수 있다.

### Worker

Kafka에서 작업을 받아 실제 영상 변환을 수행한다.

```text
Kafka 메시지 수신
→ S3에서 원본 다운로드
→ FFmpeg로 인코딩
→ 결과를 S3에 업로드
→ API에 완료 Callback
```

---

## 0-4. 전체 구조

```text
사용자
  │
  │ HTTP 요청
  ▼
ALB
  │
  ▼
API 서버 ──────────────► MySQL/RDS
  │                      사용자·프로필·작업 정보
  │
  ├──── 업로드 주소 제공 ─────► S3
  │
  └──── 작업 메시지 ──────────► Kafka
                                  │
                                  ▼
                               Worker
                                  │
                    원본 다운로드·결과 업로드
                                  │
                                  ▼
                                  S3
                                  │
                      완료 결과를 API에 통지
                                  ▼
                               API 서버
```

---

## 0-5. 일반 요청과 영상 요청의 차이

### 프로필 조회: 동기 처리

사용자는 요청이 끝날 때 바로 결과를 받는다.

```text
사용자
→ API
→ DB 조회
→ 프로필 응답
```

```json
{
  "name": "허원회",
  "birthPlace": "서울",
  "oneLineIntro": "배우 소개"
}
```

### 영상 인코딩: 비동기 처리

영상 인코딩은 몇 분이 걸릴 수도 있다. 한 번의 HTTP 요청을 인코딩이 끝날 때까지 열어 두면 사용자가 계속 기다려야 하고, 연결이 끊기거나 시간 초과가 발생할 수 있다.

그래서 OnFilm은 인코딩 완료를 기다리지 않고 작업 접수 후 작업 번호를 먼저 응답한다.

```text
사용자 → 인코딩 요청
API → 작업 번호(jobId) 즉시 응답
HTTP 연결 종료
Worker → 백그라운드에서 인코딩
사용자 → jobId로 진행 상태를 별도로 조회
```

전체 비동기 흐름은 다음과 같다.

```text
사용자
→ API에 영상 작업 접수
→ API가 jobId 응답
→ Kafka를 통해 Worker에 작업 전달
→ Worker가 영상 변환
→ Worker가 API에 완료 통지
→ 사용자가 jobId로 상태 조회
```

처음 응답:

```json
{
  "jobId": "7c45a841-...",
  "status": "REQUESTED"
}
```

완료 후 조회:

```json
{
  "jobId": "7c45a841-...",
  "status": "DONE"
}
```

> 영상이 완성될 때까지 한 번의 요청으로 기다리지 않고, 작업 번호를 먼저 받은 뒤 이후에 상태를 다시 확인한다.

### 지금 기억할 핵심

1. OnFilm은 배우의 프로필, 출연 작품과 영상을 관리해 포트폴리오로 제공하는 서비스다.
2. API는 사용자 요청과 업무 규칙을 처리하고, Worker는 CPU를 많이 사용하는 영상 변환을 담당한다.
3. DB는 사용자와 작업 정보를 저장하고, S3는 실제 파일을 저장하며, Kafka는 API와 Worker 사이에서 작업을 전달한다.

---

## 0-6. Controller → Service → Repository → DB 흐름

프로필 조회를 예로 들면 사용자는 다음 주소로 요청한다.

```http
GET /api/people/{publicId}
```

```text
사용자
  ↓ HTTP 요청
Controller
  ↓ 조회 요청
Service
  ↓ DB 조회 요청
Repository
  ↓ SQL
MySQL
  ↓ 조회 결과
Repository
  ↓ Person
Service
  ↓ ProfileResponse
Controller
  ↓ HTTP 200
사용자
```

### Controller: 요청을 받는 입구

```java
@GetMapping("/{publicId}")
public ResponseEntity<ProfileResponse> getProfile(
        @PathVariable String publicId
) {
    return ResponseEntity.ok(
        personQueryService.findProfileByPublicId(publicId)
    );
}
```

Controller는 다음 일을 한다.

- HTTP 요청을 받음
- URL에서 `publicId`를 꺼냄
- Service에 작업을 요청함
- 결과를 HTTP 응답으로 반환함

> Controller는 사용자의 요청을 자바 메서드 호출로 바꾸고, 결과를 HTTP 응답으로 바꾸는 곳이다.

### Service: 기능의 처리 과정 담당

```java
public ProfileResponse findProfileByPublicId(String publicId) {
    Person person = personRepository.findByPublicId(publicId)
            .orElseThrow(() -> new PersonNotFoundException(publicId));

    String publicUrl =
            storageService.toPublicUrl(person.getProfileImageKey());

    return ProfileResponse.from(person, publicUrl);
}
```

Service는 다음 일을 한다.

1. Repository에 프로필 조회를 요청한다.
2. 프로필이 없으면 예외를 발생시킨다.
3. 프로필 이미지의 Storage Key를 공개 URL로 변환한다.
4. `Person`을 응답용 `ProfileResponse`로 변환한다.

> Service는 해당 기능을 어떤 순서와 규칙으로 처리할지 결정하는 곳이다.

### Repository: DB 접근 담당

```java
Optional<Person> findByPublicId(String publicId);
```

Repository는 DB에서 데이터를 조회·저장·삭제한다. Spring Data JPA는 위 메서드 이름을 분석해 다음과 비슷한 SQL을 만든다.

```sql
SELECT *
FROM person
WHERE public_id = ?;
```

> Repository는 Service와 DB 사이에서 데이터 조회와 저장을 담당한다.

### DB: 실제 데이터 보관

```text
person
--------------------------------------------------
id | public_id | name | birth_date | one_line_intro
10 | abc-123   | 철수 | 1995-01-01 | 배우입니다
```

Repository가 SQL을 실행하면 DB가 행을 반환하고, JPA는 조회 결과를 `Person` 객체로 만든다.

### Response DTO: 사용자에게 보낼 형태

```java
return ProfileResponse.from(person, publicUrl);
```

Entity를 그대로 보내지 않고 DTO로 변환하면 다음 이점이 있다.

- DB 내부 `id`처럼 공개하지 않을 값을 제외
- 응답에 필요한 정보만 전달
- Storage Key를 접근 가능한 URL로 변환
- Entity 구조와 외부 API 응답을 분리

### 역할을 나누는 이유

```text
Controller : 요청과 응답
Service    : 기능 처리와 업무 규칙
Repository : DB 접근
DB         : 데이터 보관
```

기능이 커져도 한 클래스에 모든 책임이 섞이지 않게 한다.

> Controller가 사용자 요청을 받고, Service가 기능을 처리하며, Repository가 DB에서 데이터를 조회하거나 저장한다.

---

## 0-7. Entity와 DTO의 차이

프로필 수정 기능에는 세 종류의 객체가 등장한다.

```text
UpdatePersonRequest → Person → ProfileResponse
요청 DTO              Entity   응답 DTO
```

### 요청 DTO

```java
public record UpdatePersonRequest(
    @NotBlank String name,
    @PastOrPresent LocalDate birthDate,
    String birthPlace,
    String oneLineIntro,
    String profileImageKey,
    List<CreatePersonSnsRequest> snsList,
    List<String> rawTags
) {
}
```

요청 DTO의 역할은 다음과 같다.

- 사용자가 보낸 데이터 받기
- 필수값과 길이 같은 입력 형식 검사
- API가 받을 수 있는 값만 정의
- HTTP 요청 형식을 Entity와 분리

> 요청 DTO는 사용자가 서버에 전달한 값을 담는 상자다.

### Entity

```java
@Entity
public class Person {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private LocalDate birthDate;
    private String birthPlace;
    private String oneLineIntro;
    private String profileImageKey;
    private String publicId;

    public void changeBasicInfo(...) {
        applyBasicInfo(...);
    }
}
```

Entity의 역할은 다음과 같다.

- DB 테이블과 연결
- 현재 데이터 보관
- 값을 안전하게 변경하는 규칙 관리
- 다른 Entity와의 관계 관리

> Entity는 DB에 저장되는 데이터와 그 데이터를 안전하게 변경하는 규칙을 관리하는 객체다.

### 응답 DTO

```java
public record ProfileResponse(
    String publicId,
    String name,
    LocalDate birthDate,
    String birthPlace,
    String oneLineIntro,
    String profileImageUrl
) {
}
```

Entity에서 사용자에게 필요한 값만 골라 응답한다.

```java
ProfileResponse.from(person, publicUrl);
```

> 응답 DTO는 서버의 처리 결과를 사용자에게 보여줄 형태로 담는 상자다.

### Entity를 요청과 응답에 그대로 사용하지 않는 이유

- 내부 `id`, 연관된 `User` 등의 정보가 노출될 수 있음
- 사용자가 `publicId`, 소유자 등 허용하지 않은 값까지 변경할 수 있음
- Entity 필드 변경이 외부 API 형식 변경으로 이어질 수 있음
- DB의 Storage Key와 응답의 공개 URL처럼 필요한 값의 형태가 다를 수 있음

```text
사용자 JSON
    ↓
UpdatePersonRequest
    ↓ 입력값 검사
Service
    ↓
Person.changeBasicInfo()
    ↓
DB 저장
```

조회는 반대 방향이다.

```text
DB
  ↓
Person
  ↓ 필요한 값 선택·변환
ProfileResponse
  ↓
사용자 JSON
```

> 요청 DTO는 사용자의 입력을 받고, Entity는 DB 상태와 변경 규칙을 관리하며, 응답 DTO는 사용자에게 필요한 결과만 전달한다.

---

## 0-8. Command와 Query 분리

OnFilm의 Service는 다음처럼 나뉜다.

```text
PersonCommandService / PersonQueryService
FilmographyCommandService / FilmographyQueryService
StoryboardCommandService / StoryboardQueryService
```

### Command

Command는 시스템의 데이터를 등록·수정·삭제한다.

```text
프로필 수정
SNS 추가
작품 등록
스토리보드 삭제
갤러리 순서 변경
```

```java
@Transactional
public class PersonCommandService {
    public void updateProfile(
            String publicId,
            UpdatePersonRequest request
    ) {
        Person person =
                currentPersonProvider.getRequired(publicId);

        person.changeBasicInfo(...);
        person.replaceSns(...);
        person.replaceProfileTags(...);
    }
}
```

### Query

Query는 데이터를 변경하지 않고 현재 값을 읽어 반환한다.

```text
프로필 조회
필모그래피 목록 조회
스토리보드 조회
영상 작업 상태 조회
```

```java
@Transactional(readOnly = true)
public class PersonQueryService {
    public ProfileResponse findProfileByPublicId(String publicId) {
        Person person = findByPublicId(publicId);
        String publicUrl = storageService.toPublicUrl(
                person.getProfileImageKey()
        );
        return ProfileResponse.from(person, publicUrl);
    }
}
```

메서드가 끝난 뒤 DB 상태가 바뀌는지로 구분할 수 있다.

| 기능 | 분류 |
|---|---|
| 프로필 조회 | Query |
| 프로필 수정 | Command |
| 작품 목록 조회 | Query |
| 작품 등록 | Command |
| 장면 순서 변경 | Command |
| 작업 상태 조회 | Query |

### Service를 나눈 이유

하나의 `PersonService`에 조회, 수정, 파일 작업을 모두 넣으면 기능이 커질수록 책임이 섞인다. OnFilm은 역할별로 나눴다.

```text
PersonQueryService   → 프로필 조회
PersonCommandService → 프로필 정보 변경
PersonMediaService   → 프로필 이미지 같은 파일 작업
```

Command와 Query용 DB를 따로 사용한다는 뜻은 아니다. 두 Service 모두 같은 MySQL을 사용하며, 조회 코드와 변경 코드의 책임만 구분한 구조다.

> Command는 데이터를 변경하고 Query는 데이터를 조회하며, OnFilm은 두 역할이 한 Service에 섞이지 않도록 클래스를 분리했다.

---

## 0-9. 트랜잭션의 역할

트랜잭션은 여러 DB 작업을 하나의 작업 단위로 묶는다.

```java
person.changeBasicInfo(...);
person.replaceSns(...);
person.replaceProfileTags(...);
```

사용자 관점에서 이 세 작업은 하나의 프로필 수정이다.

### 트랜잭션이 없다면

```text
1. 기본 정보 변경 성공
2. SNS 변경 성공
3. 프로필 태그 변경 실패
```

일부 정보만 반영된 불완전한 상태가 DB에 남을 수 있다.

### 트랜잭션이 있다면

```java
@Transactional
public class PersonCommandService {
}
```

```text
트랜잭션 시작
  ↓
기본 정보 변경
  ↓
SNS 변경
  ↓
프로필 태그 변경
  ↓
모두 성공
  ↓
Commit
```

중간에 예외가 발생하면 앞의 변경도 취소한다.

```text
트랜잭션 시작
  ↓
기본 정보 변경
  ↓
SNS 변경
  ↓
프로필 태그 변경 중 예외
  ↓
Rollback
```

- Commit: 트랜잭션의 변경 내용을 최종 확정
- Rollback: 트랜잭션 안에서 수행한 변경 내용을 취소

> 모두 성공하거나, 하나라도 실패하면 모두 취소한다.

### 조회 트랜잭션

```java
@Transactional(readOnly = true)
```

조회 전용이라는 의도를 표시하고, Service가 응답을 만드는 동안 JPA가 조회한 Entity를 관리할 수 있게 한다.

LAZY 연관 데이터가 필요한 경우 트랜잭션 안에서는 JPA가 추가 SQL을 실행해 가져올 수 있다. 영속성 컨텍스트가 끝나 관리에서 벗어난 Entity의 LAZY 데이터에 처음 접근하면 `LazyInitializationException`이 발생할 수 있다.

### 트랜잭션이 묶을 수 있는 범위

일반적인 DB 트랜잭션은 같은 DB 안의 작업을 묶는다.

```text
Person 변경
SNS 변경
ProfileTag 변경
```

MySQL 저장, S3 업로드, Kafka 전송처럼 서로 다른 시스템의 작업은 일반 DB 트랜잭션 하나로 쉽게 묶을 수 없다. 이 문제는 파일 관리와 영상 처리 단원에서 별도로 학습한다.

> 트랜잭션은 하나의 기능에 포함된 여러 DB 작업을 묶어, 모두 성공하면 커밋하고 하나라도 실패하면 모두 롤백한다.

---

## 0-10. JPA 영속성 컨텍스트

영속성 컨텍스트는 JPA가 조회하거나 저장한 Entity를 관리하는 애플리케이션 내부의 메모리 공간이다.

```text
애플리케이션
┌─────────────────────────────┐
│ 영속성 컨텍스트              │
│ ID 10 → Person 객체         │
│ ID 20 → Movie 객체          │
└─────────────────────────────┘
              ↕
           MySQL DB
```

### Entity 조회 과정

```java
Person person = personRepository.findById(10L)
        .orElseThrow();
```

1. 영속성 컨텍스트에 ID 10인 `Person`이 있는지 확인한다.
2. 없으면 DB에 SELECT를 실행한다.
3. 조회 결과로 `Person` 객체를 만든다.
4. 영속성 컨텍스트에 보관한다.
5. 코드에 `Person`을 반환한다.

### 1차 캐시

같은 영속성 컨텍스트 안에서 같은 ID의 Entity를 다시 찾으면 보관 중인 객체를 반환할 수 있다.

```java
Person first = personRepository.findById(10L).orElseThrow();
Person second = personRepository.findById(10L).orElseThrow();

first == second; // 일반적으로 true
```

1차 캐시는 애플리케이션 전체가 공유하는 캐시가 아니라 일반적으로 하나의 트랜잭션이나 요청에서 사용하는 짧은 범위의 캐시다.

### Entity의 네 가지 상태

#### 비영속

자바 객체를 만들었지만 JPA가 아직 관리하지 않는 상태다.

```java
Person person = Person.create(...);
```

#### 영속

Repository로 저장하거나 조회해 JPA가 관리하는 상태다. 영속 상태에서는 Dirty Checking이 동작한다.

#### 준영속

처음에는 관리됐지만 영속성 컨텍스트가 끝나 JPA의 관리에서 벗어난 상태다. 값을 바꿔도 Dirty Checking으로 자동 저장되지 않는다.

#### 삭제

JPA에 삭제를 요청한 상태다. Flush 시점에 DELETE SQL이 실행된다.

### 프로필 수정과 영속성 컨텍스트

```text
@Transactional 시작
        ↓
Person을 DB에서 조회
        ↓
영속성 컨텍스트가 Person 관리
        ↓
changeBasicInfo()로 값 변경
        ↓
JPA가 변경 감지
        ↓
UPDATE SQL 실행
        ↓
Commit
```

영속성 컨텍스트는 같은 Entity 관리, 1차 캐시, 변경 감지, SQL 실행 시점 조절과 LAZY 연관 데이터 조회 등을 담당한다.

> 영속성 컨텍스트는 JPA가 조회하거나 저장한 Entity를 메모리에서 관리하며, 반복 조회와 변경 감지 등을 담당하는 공간이다.

---

## 0-11. Dirty Checking, Flush, Commit

```text
Dirty Checking → 무엇이 바뀌었는지 확인
Flush          → 변경 내용을 SQL로 DB에 전달
Commit         → DB 변경을 최종 확정
```

### 프로필 수정 흐름

```java
@Transactional
public void updateProfile(...) {
    Person person = personRepository.findById(10L)
            .orElseThrow();
    person.changeBasicInfo(...);
}
```

1. JPA가 `Person`을 조회하고 최초 상태를 기억한다.
2. `changeBasicInfo()`가 메모리의 객체를 변경한다.
3. Dirty Checking으로 최초 상태와 현재 상태를 비교한다.
4. 값이 달라졌으면 Flush 시 UPDATE SQL을 DB에 전달한다.
5. 트랜잭션 Commit으로 변경을 최종 확정한다.

```sql
UPDATE person
SET name = '김철수',
    birth_place = '부산'
WHERE id = 10;
```

### Flush와 Commit의 차이

```text
Flush
→ JPA가 SQL을 DB에 전달
→ 트랜잭션은 아직 끝나지 않음
→ Rollback 가능

Commit
→ 트랜잭션 정상 종료
→ DB 변경 최종 확정
```

Flush 후 예외가 발생하면 Rollback으로 앞서 실행한 SQL의 변경도 취소할 수 있다.

### Flush가 발생하는 대표 시점

1. 트랜잭션 Commit 직전
2. `entityManager.flush()` 또는 `saveAndFlush()` 직접 호출
3. 현재 변경을 쿼리 결과에 반영해야 하는 JPQL 실행 전

### 조회한 Entity 변경에 `save()`가 필요 없는 이유

```java
Person person = personRepository.findById(10L).orElseThrow();
person.changeBasicInfo(...);
```

조회한 `Person`은 영속 상태이므로 JPA가 변경을 감지한다.

```text
새 Entity 저장     → save() 필요
조회한 Entity 변경 → Dirty Checking으로 저장
```

DB 제약조건 위반은 값을 자바 객체에 넣을 때가 아니라 INSERT나 UPDATE SQL이 실제 실행되는 Flush 시점에 발견될 수 있다.

> JPA는 Dirty Checking으로 Entity의 변경을 찾고, Flush로 SQL을 DB에 전달한 뒤, Commit으로 변경을 최종 확정한다.

---

## 0-12. 예외가 HTTP 오류 응답으로 바뀌는 과정

존재하지 않는 프로필을 요청한다고 가정한다.

```http
GET /api/people/not-exist-id
```

```java
Person person = personRepository.findByPublicId(publicId)
        .orElseThrow(() -> new PersonNotFoundException(publicId));
```

`PersonNotFoundException`을 자바 예외 그대로 노출하면 내부 클래스와 코드 위치 등이 드러날 수 있다. OnFilm은 예외를 일정한 JSON 형식으로 변환한다.

```json
{
  "code": "PERSON_NOT_FOUND",
  "message": "인물 정보를 찾을 수 없습니다.",
  "errors": []
}
```

```text
PersonNotFoundException 발생
→ GlobalExceptionHandler가 받음
→ ErrorCode에서 HTTP 상태와 메시지 확인
→ ErrorResponse 생성
→ HTTP 404 + JSON 응답
```

### DomainException

예측 가능한 기능 실패는 `DomainException`으로 표현한다.

```java
public abstract class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
}
```

```text
PersonNotFoundException
DuplicateEmailException
ForbiddenPersonAccessException
InvalidRefreshTokenException
MediaUploadRequestExpiredException
```

### ErrorCode

오류별 HTTP 상태와 사용자 메시지를 정의한다.

| ErrorCode | HTTP 상태 | 의미 |
|---|---:|---|
| `INVALID_CREDENTIALS` | 401 | 로그인 정보 오류 |
| `FORBIDDEN_PERSON_ACCESS` | 403 | 다른 사람의 프로필 수정 시도 |
| `PERSON_NOT_FOUND` | 404 | 프로필을 찾을 수 없음 |
| `DUPLICATE_EMAIL` | 409 | 이미 등록된 이메일 |
| `VALIDATION_FAILED` | 422 | 입력값 검증 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 서버 오류 |

### GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ErrorResponse.of(
                    errorCode.name(),
                    errorCode.message()
                ));
    }
}
```

- `@RestControllerAdvice`: 여러 Controller에서 발생한 예외를 공통으로 받아 JSON 응답으로 변환
- `@ExceptionHandler(DomainException.class)`: `DomainException`과 그 자식 예외를 해당 메서드가 처리

### ErrorResponse

```java
public record ErrorResponse(
    String code,
    String message,
    List<FieldError> errors
) {
}
```

- `code`: 프론트엔드가 오류 종류를 구분하는 값
- `message`: 사용자에게 보여줄 설명
- `errors`: 입력값 검증처럼 잘못된 필드의 상세 목록

입력값 검증 실패 예시는 다음과 같다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    {
      "field": "name",
      "message": "이름은 필수입니다."
    },
    {
      "field": "birthDate",
      "message": "생년월일은 미래일 수 없습니다."
    }
  ]
}
```

예상하지 못한 `RuntimeException`은 서버에 자세한 로그를 남기고, 사용자에게는 내부 정보를 숨긴 공통 500 응답을 보낸다.

Command Service에서 `DomainException`이 발생하면 `RuntimeException`의 자식이므로 기본적으로 트랜잭션도 Rollback된다.

> OnFilm은 기능 처리 중 발생한 예외를 `GlobalExceptionHandler`에서 받아, 정해진 HTTP 상태와 `ErrorResponse` JSON으로 변환한다.

---

## 0-13. 체크 예외와 언체크 예외

```text
Throwable
├─ Error
└─ Exception
   ├─ 체크 예외
   └─ RuntimeException
      └─ 언체크 예외
```

### 체크 예외

`Exception`을 상속하지만 `RuntimeException`은 상속하지 않는 예외다.

```text
IOException
SQLException
FileNotFoundException
```

컴파일러가 처리 여부를 확인하므로 `try-catch`로 처리하거나 `throws`로 호출한 쪽에 넘겨야 한다.

```java
public String readFile() throws IOException {
    return Files.readString(path);
}
```

체크 예외도 프로그램 실행 중 발생한다. 다만 컴파일러가 예외 처리 코드의 존재를 미리 검사한다.

### 언체크 예외

`RuntimeException`을 상속하며 `try-catch`나 `throws` 작성을 강제하지 않는다.

```text
IllegalArgumentException
NullPointerException
IllegalStateException
DomainException
```

OnFilm의 기능 예외는 `DomainException`을 상속하므로 언체크 예외다. 발생한 예외는 계층마다 억지로 잡지 않고 `GlobalExceptionHandler`까지 전달할 수 있다.

### Spring의 기본 Rollback 기준

```text
언체크 예외(RuntimeException) → Rollback
체크 예외(Exception)          → 기본 설정에서는 Rollback하지 않음
```

체크 예외에도 Rollback이 필요하면 명시한다.

```java
@Transactional(rollbackFor = IOException.class)
public void updateProfile(...) throws IOException {
}
```

| 구분 | 체크 예외 | 언체크 예외 |
|---|---|---|
| 상속 | `Exception` | `RuntimeException` |
| 처리 강제 | `catch` 또는 `throws` 필요 | 처리 강제 없음 |
| 발생 시점 | 실행 중 | 실행 중 |
| 컴파일러 확인 | 처리 여부 확인 | 확인하지 않음 |
| Spring 기본 Rollback | 하지 않음 | 수행 |
| 예시 | `IOException` | `IllegalArgumentException` |
| OnFilm 예시 | 거의 사용하지 않음 | `DomainException` |

> 체크 예외는 컴파일러가 처리 여부를 확인하고, 언체크 예외는 처리 여부를 강제하지 않는다. OnFilm의 `DomainException`은 언체크 예외로 구성했다.

---

## 0-14. 하나의 요청을 코드에서 끝까지 추적하는 방법

Entity부터 무작정 읽기보다 사용자의 HTTP 요청이 들어오는 지점부터 따라간다.

```text
HTTP 요청
→ Controller
→ Request DTO
→ Service
→ Repository
→ Entity
→ Response DTO
→ 예외 처리
→ Test
```

### 1. HTTP 주소와 Controller 확인

```http
PUT /api/people/{publicId}
```

```java
@PutMapping("/{publicId}")
public ResponseEntity<String> updatePerson(
        @PathVariable String publicId,
        @Valid @RequestBody UpdatePersonRequest request
) {
    personCommandService.updateProfile(publicId, request);
    return ResponseEntity.ok(publicId);
}
```

다음 내용을 확인한다.

```text
HTTP 방식    : PUT
URL 값       : publicId
요청 본문    : UpdatePersonRequest
호출 Service : PersonCommandService
호출 메서드  : updateProfile()
응답          : HTTP 200 + publicId
```

### 2. Request DTO 확인

- 사용자가 어떤 값을 보낼 수 있는가?
- 필수값과 길이 제한은 무엇인가?
- 잘못된 입력은 무엇인가?
- 중첩 DTO가 있는가?

### 3. Service와 보조 Service 확인

```java
Person person = currentPersonProvider.getRequired(publicId);
```

이 코드를 보고 넘어가지 않고 내부 흐름도 확인한다.

```text
현재 로그인한 사용자 ID 확인
→ User 조회
→ 연결된 Person 확인
→ 요청한 publicId와 비교
→ 다르면 접근 거부
```

### 4. Entity 변경 메서드 확인

```java
person.changeBasicInfo(...);
person.replaceSns(...);
person.replaceProfileTags(...);
```

필드만 보지 않고 값 검증, 정규화와 상태 변경 규칙을 확인한다.

### 5. Repository와 저장 방법 확인

- 어떤 조건으로 조회하는가?
- `Optional`을 반환하는가?
- Fetch Join이나 Lock을 사용하는가?
- 파생 쿼리, JPQL, QueryDSL 중 무엇인가?
- 새 Entity를 `save()`하는가, 영속 Entity를 Dirty Checking으로 변경하는가?

### 6. 응답과 예외 확인

```text
정상 응답
→ HTTP 200 + publicId

로그인하지 않음
→ AuthenticationRequiredException
→ HTTP 401

다른 사용자의 프로필 수정
→ ForbiddenPersonAccessException
→ HTTP 403

요청값 검증 실패
→ MethodArgumentNotValidException
→ HTTP 422
```

### 7. 테스트 확인

```text
Controller Test → HTTP 요청과 응답
Service Test    → 기능 처리 순서와 권한
Entity Test     → 값 검증과 상태 변경 규칙
통합 Test       → 실제 DB 저장과 트랜잭션
```

테스트를 보면 개발자가 어떤 실패 상황과 규칙을 중요하게 생각했는지 알 수 있다.

### IntelliJ 이동 방법

```text
Command + 클릭 또는 Command + B → 선언부로 이동
Command + [                     → 이전 위치
Option + F7                     → 사용처 찾기
Shift 두 번                     → 전체 검색
```

### 기능별 정리 양식

```text
기능: 프로필 수정
요청: PUT /api/people/{publicId}
Controller: PersonController.updatePerson()
Request DTO: UpdatePersonRequest
Service: PersonCommandService.updateProfile()
Repository: UserRepository, PersonRepository
변경 Entity: Person, PersonSns, ProfileTag
저장 방식: @Transactional + Dirty Checking
성공 응답: HTTP 200 + publicId
실패: 401 / 403 / 422 / 400
```

> 프로젝트 코드는 HTTP 주소에서 시작해 Controller, DTO, Service, Repository, Entity, 예외와 테스트 순서로 따라가야 하나의 기능을 온전히 이해할 수 있다.

---

## 0번 단원 최종 정리

- OnFilm의 사용자 기능과 전체 시스템 구성을 설명할 수 있다.
- 동기 요청과 비동기 영상 처리의 차이를 설명할 수 있다.
- Controller, Service, Repository, Entity, DTO의 역할을 구분할 수 있다.
- Command와 Query를 구분할 수 있다.
- 트랜잭션, 영속성 컨텍스트, Dirty Checking, Flush와 Commit을 연결해 설명할 수 있다.
- 코드 예외가 HTTP 오류 응답으로 변환되는 과정을 설명할 수 있다.
- 체크 예외와 언체크 예외, Spring의 기본 Rollback 차이를 설명할 수 있다.
- HTTP 요청 하나를 코드와 테스트까지 따라갈 수 있다.
