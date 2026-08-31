# Domain Validation 위치 결정 가이드

- 기준일: 2026-08-25
- 대상: OnFilm의 DTO, Service, Policy, Entity·Value Object, DB 검증
- 관련 작업: 엔티티 불변식 정비, DTO record 전환, DomainException·ErrorCode 표준화
- 상태: 현재 구현 기준 완료

## 목적

검증 코드를 작성할 때 가장 먼저 결정해야 하는 것은 `if`문이나 annotation의 형태가 아니라 **그 규칙을 어느 계층이 소유하는가**이다. 이 문서는 다음 질문에 일관되게 답하기 위한 기준이다.

- `@NotNull`, `@NotBlank`, `@Size`는 DTO에만 두면 되는가?
- Service에서 `== null`, `isBlank()`를 다시 검사해야 하는가?
- Entity 생성자와 변경 메서드는 무엇을 검증해야 하는가?
- 중복 검사는 Service와 DB 중 어디에 두어야 하는가?
- Repository나 외부 스토리지가 필요한 규칙도 Domain Validation인가?

## 한 문장 원칙

> 객체가 어떤 진입 경로로 사용돼도 항상 지켜야 하는 규칙은 Entity·Value Object에, 현재 요청과 사용자·저장소·외부 상태가 있어야 판단할 수 있는 규칙은 DTO·Service·Policy에, 동시성에서도 깨지면 안 되는 무결성은 DB에 둔다.

검증 위치는 입력이 HTTP에서 왔다는 사실만으로 결정하지 않는다. **판단에 필요한 정보와 실패를 막아야 하는 최종 시점**으로 결정한다.

## 전체 흐름

```text
HTTP Request
  → DTO / Controller Boundary
      요청 형식과 사용자 입력 오류
  → Application Service
      존재·권한·소유권·유스케이스 조건
  → Domain Policy
      여러 객체가 공유하는 순수 도메인 규칙
  → Entity / Value Object
      생성·변경 후에도 항상 지켜야 하는 불변식
  → Database
      동시 요청까지 포함한 최종 무결성
```

각 계층은 앞 계층을 믿고 검증을 생략하는 것이 아니라 자신의 책임만 독립적으로 지킨다. 같은 제약이 여러 계층에 표현되는 것은 허용하지만, 같은 목적의 코드를 Service에 그대로 복사하지 않는다.

## 위치별 책임 요약

| 위치 | 판단 질문 | 대표 검증 | 실패 목적 |
| --- | --- | --- | --- |
| DTO | 요청 형식이 API 계약에 맞는가? | null, blank, 길이, 숫자 범위, 배열 원소, 요청 전용 필드 관계 | DB·도메인 호출 전 빠르고 친절한 422 응답 |
| Service | 이 사용자가 지금 이 유스케이스를 실행할 수 있는가? | 존재, 권한, 소유권, 외부 파일 존재, 사전 중복, transaction·lock 후 재검증 | 유스케이스 실패를 의미 있는 `DomainException`으로 표현 |
| Policy / Provider | 여러 객체에서 공유하지만 한 Entity에 넣기 어려운 규칙인가? | storage key 형식·소유권, 현재 사용자 해석, 장르 정규화·연결 | 규칙 중복을 제거하고 Service 흐름과 분리 |
| Entity / Value Object | 이 객체가 어떤 호출 경로에서도 유효한가? | 필수값, 정규화, 필드 관계, 상태 전이, 연관관계, 컬렉션 중복·순서 | 잘못된 객체의 생성과 상태 변경 자체를 차단 |
| DB | 경쟁 요청이나 우회 저장에서도 데이터가 유효한가? | NOT NULL, FK, UNIQUE, CHECK, `@Version` | commit 시점의 최종 데이터 무결성 보장 |

## 1. DTO: 요청 형식 검증

DTO는 HTTP·메시지 같은 외부 입력이 API 계약을 만족하는지 검증한다.

```java
public record StoryboardProjectRequest(
        @NotBlank(message = "스토리보드 제목은 필수입니다.")
        @Size(max = StoryboardProject.TITLE_MAX_LENGTH)
        String title
) {
}
```

DTO에 둘 규칙은 다음과 같다.

- 필수 요청 필드: `@NotNull`, `@NotBlank`
- 문자열과 컬렉션 크기: `@Size`
- 숫자 범위: `@Positive`, `@Min`, `@Max`
- 외부 표현 형식: `@Email`, `@Pattern`
- 중첩 요청: 필드와 컬렉션 원소의 `@Valid`
- 비밀번호 확인처럼 요청에서만 존재하는 필드 관계: class-level custom constraint

DTO 검증은 사용자에게 필드 단위 오류를 빠르게 반환하기 위한 API 경계다. Entity를 보호하는 최종 방어선은 아니다.

```text
DTO 검증 실패
  → 422 VALIDATION_FAILED
  → ErrorResponse.errors에 필드 오류 포함
```

Controller에서 `@Valid` 또는 메서드 파라미터 검증을 위한 `@Validated`를 빠뜨리면 annotation은 실행되지 않는다.

### DTO에 두지 않을 규칙

- 로그인 사용자가 해당 Person의 소유자인가?
- `movieId`가 실제로 존재하는가?
- 이메일이 이미 DB에 등록됐는가?
- storage key의 소유자 ID가 현재 사용자와 일치하는가?
- Job이 현재 `PROCESSING`에서 `DONE`으로 전이할 수 있는가?

이 규칙들은 요청 객체만으로 판단할 수 없거나 객체의 본질적인 불변식이므로 다른 위치가 소유한다.

## 2. Service: 유스케이스 검증

Service는 Repository, 인증 사용자, 트랜잭션과 외부 시스템이 있어야 판단할 수 있는 조건을 검증한다.

대표 책임은 다음과 같다.

- Entity와 Aggregate 존재 여부
- 현재 사용자 인증과 편집 권한
- URL의 `publicId`와 현재 Person의 소유권
- Repository를 이용한 빠른 중복 확인
- 외부 스토리지 객체의 실제 존재 여부
- 여러 Aggregate 사이 연결 가능 여부
- 읽기 이후 상태가 바뀔 수 있는 흐름의 최종 재검증
- lock과 transaction이 필요한 동시성 조건

```java
Person person = currentPersonProvider.getRequired(publicId);
Movie movie = movieRepository.findById(movieId)
        .orElseThrow(() -> new MovieNotFoundException(movieId));

if (!storageService.exists(sourceKey)) {
    throw new MediaSourceFileNotFoundException();
}
```

이 실패는 API 소비자가 분기할 의미가 있으므로 전용 `DomainException`과 `ErrorCode`를 사용한다. Service가 HTTP 상태를 직접 결정하지는 않는다.

### Service의 null 검사 기준

Controller의 `@Valid`를 통과한 DTO 필드의 null·blank·길이를 Service에서 그대로 반복하지 않는다.

```java
// 지양: DTO와 Entity 검증을 Service에 복사
if (request.title() == null || request.title().isBlank()) {
    throw new IllegalArgumentException("title is required");
}
if (request.title().length() > StoryboardProject.TITLE_MAX_LENGTH) {
    throw new IllegalArgumentException("title is too long");
}
```

```java
// 권장: Service는 유스케이스를 조정하고 Entity가 필드 불변식을 검증
StoryboardProject project = findOwnedProject(publicId, projectId);
project.changeTitle(request.title());
```

다만 Service public API 자체의 계약을 보호하기 위해 request 객체 전체나 내부 호출용 ID를 방어적으로 확인할 수 있다. 이는 DTO 필드 규칙의 재구현이 아니라 잘못된 프로그래밍 호출을 빠르게 발견하기 위한 guard clause다.

## 3. Policy와 Provider: 공유되는 문맥 규칙

규칙이 도메인 의미를 가지지만 한 Entity의 내부 상태만으로 판단하기 어렵거나 여러 Service에서 반복되면 별도 Policy·Provider 컴포넌트로 분리한다.

현재 예시는 다음과 같다.

| Component | 검증 책임 | Entity에 두지 않은 이유 |
| --- | --- | --- |
| `StorageKeyPolicy` | key 형식과 Person·Movie·JobType 소유 prefix | 현재 소유자 ID와 용도 문맥이 필요하고 여러 Entity·Service가 공유 |
| `CurrentPersonProvider` | 인증 userId를 Person으로 해석하고 path `publicId` 소유권 확인 | SecurityContext와 Repository가 필요한 애플리케이션 경계 |
| `MovieGenreNormalizer` | 표준·사용자 Genre 정규화, 활성 상태와 Movie 연결 | Genre Repository 조회와 여러 객체의 조정이 필요 |
| `RawPasswordPolicy` | BCrypt 입력 전 비밀번호 길이·UTF-8 byte 정책 | User에는 평문 비밀번호를 전달하거나 보관하지 않기 때문 |

Policy는 단순히 `Utils`라는 이름으로 검증을 모으는 장소가 아니다. 명확한 정책 이름과 입력·출력 계약을 가지고 상태 변경 orchestration이나 HTTP 응답 생성을 하지 않는다.

## 4. Entity와 Value Object: 도메인 불변식

Entity와 Value Object는 Controller, Service, batch, event listener, 테스트 중 어디에서 호출돼도 유효한 상태만 만들 수 있어야 한다.

```java
private StoryboardProject(String title) {
    this.title = requireTitle(title);
}

public void changeTitle(String title) {
    this.title = requireTitle(title);
}
```

Entity에 둘 규칙은 다음과 같다.

- 생성과 변경 시 항상 필요한 값
- trim, 대소문자와 Unicode 같은 정규화
- 도메인 길이와 값 범위
- 역할에 따라 다른 필드 조건
- 허용되는 상태 전이
- 부모·자식 연결과 재할당 금지
- 자식 중복, 최대 개수와 순서
- 재정렬 입력이 기존 자식의 정확한 순열인지

예를 들어 `MoviePersonRole`에서 ACTOR 역할일 때 cast type을 필수로 하고, 감독·작가 역할에는 배우 상세 정보를 허용하지 않는 규칙은 HTTP 요청 형식이 아니다. 어떤 경로로 역할을 생성해도 지켜야 하므로 Entity 불변식이다.

Value Object는 값 자체의 규칙과 정규화가 반복되거나 독립적인 도메인 의미를 가질 때 사용한다.

- `UserEmail`: 이메일 정규화와 형식
- `Username`: 표시값과 중복 비교용 정규화 값
- `GenreName`: 표시 이름과 정규화 이름

Value Object를 사용하면 Service와 여러 Entity에서 같은 문자열 검증을 복사하지 않아도 된다.

### Entity annotation만으로 충분하지 않은 이유

```java
@Column(nullable = false, length = TITLE_MAX_LENGTH)
private String title;
```

`@Column`은 Java 객체 생성 시 실행되는 검증 코드가 아니다. 잘못된 값이 flush·commit 시점까지 살아 있을 수 있고, JPA 없이 생성한 객체에는 아무 효과가 없다. 따라서 `@Column`은 Entity 메서드 검증을 대체하지 않는다.

## 5. DB: 최종 무결성

DB는 애플리케이션 사전 검증이 막지 못하는 동시 요청과 우회 저장의 최종 방어선이다.

- `NOT NULL`
- FK
- 단일·복합 `UNIQUE`
- 필요한 `CHECK`
- optimistic locking을 위한 version

중복은 Service와 DB가 서로 다른 목적으로 함께 검증한다.

```text
Service exists 검사
  → 정상적인 중복 요청을 빠르고 친절하게 거부

DB UNIQUE constraint
  → 두 요청이 동시에 exists 검사를 통과해도 하나만 commit

DataIntegrityViolationException 변환
  → DB 제약 이름을 의미 있는 DomainException으로 변환
```

Service의 `existsByEmail()`만으로는 경쟁 조건을 막을 수 없다. 반대로 DB UNIQUE에만 의존하면 대부분의 중복 요청이 flush 이후 infrastructure 예외로 실패하므로 명확한 사용자 경험을 제공하기 어렵다.

## 검증 중복과 방어의 차이

스토리보드 제목의 필수·길이 규칙은 DTO, Entity와 DB에 모두 표현된다. 이는 불필요한 중복이 아니라 서로 다른 실패 지점을 보호하는 계층형 방어다.

| 위치 | 같은 제목 규칙을 두는 이유 |
| --- | --- |
| DTO `@NotBlank`, `@Size` | 잘못된 HTTP 요청을 Entity 호출 전에 필드 오류로 반환 |
| Entity `requireTitle()` | HTTP를 거치지 않은 호출에서도 유효한 Project만 생성·변경 |
| DB `NOT NULL`, `VARCHAR(120)` | 애플리케이션 버그·우회 저장에서도 최종 데이터 무결성 유지 |

반면 Service에 동일한 `title == null`, `isBlank()`, `length > 120` 코드를 다시 작성하는 것은 새로운 방어 지점을 만들지 않고 정책원만 늘리므로 피한다.

## 위치 결정 순서

새 검증은 아래 질문을 위에서부터 적용한다.

```text
1. HTTP·메시지 요청의 모양만으로 판단 가능한가?
   → DTO

2. 객체가 존재하는 동안 항상 참이어야 하는가?
   → Entity 또는 Value Object

3. 여러 객체가 공유하는 순수 정책인가?
   → Policy

4. Repository·현재 사용자·외부 시스템이 필요한가?
   → Service

5. 동시 요청에서도 절대 깨지면 안 되는가?
   → DB constraint 또는 lock/version을 함께 적용
```

여러 질문에 해당할 수 있다. 그 경우 한곳만 선택하는 것이 아니라 각 위치가 자신의 목적에 맞는 표현을 갖는다.

## 대표 사례

### Storyboard 제목

```text
DTO: @NotBlank, @Size
  → Service: Project 존재와 현재 Person 소유권
  → Entity: trim, 필수값, 최대 길이
  → DB: NOT NULL, column length
```

### 회원가입 이메일과 username

```text
DTO: 필수값·형식·길이
  → Value Object: 정규화와 도메인 형식
  → Service: exists 사전 확인
  → DB: email·usernameNormalized UNIQUE
  → Service: 제약 충돌을 Duplicate*Exception으로 변환
```

### Storyboard Card image key

```text
DTO: 선택값 길이
  → StorageKeyPolicy: key 구조와 현재 Person 소유 prefix
  → Entity: 선택값 정규화와 최대 길이
  → DB: column length와 Scene FK
```

### Media Encode 완료

```text
DTO: requestId·sourceKey·contentType 필수와 길이
  → Service: UploadRequest 존재·사용자·Movie·만료·원본 파일 존재
  → Entity: 상태 전이와 요청 snapshot 일치
  → DB: requestId UNIQUE, pessimistic lock, Job requestId UNIQUE
```

### MoviePerson 역할과 배역

```text
DTO: roles 목록 필수·최대 개수·중복과 역할별 입력 조합
  → MoviePersonRole: ACTOR일 때 castType 필수, 비배우 역할의 배우 정보 거부
  → MoviePerson: 역할 최소 1개와 동일 역할 중복 방지
  → Movie: 같은 Person 참여 중복 방지
  → DB: Movie·Person 복합 UNIQUE, 참여·역할 복합 UNIQUE와 FK·CHECK
```

## 요청 종류별 주의점

### 생성과 전체 수정

필수 필드를 DTO에 명시하고 Entity factory·change 메서드가 같은 불변식을 지킨다.

### 부분 수정

`null`이 “변경하지 않음”인지 “값 삭제”인지 먼저 API 계약으로 정한다. 이 의미를 DTO 또는 command type에서 구분하지 않으면 Entity가 의도를 판단할 수 없다.

### 내부 Callback과 메시지

HTTP Bean Validation을 통과해도 서명, schema version, timestamp·nonce, Job snapshot 일치처럼 transport와 보안 문맥 검증이 별도로 필요하다. 이후 Entity 상태 전이를 다시 검증한다.

### 설정값

bucket, retention, timeout 같은 설정 검증은 Domain Entity가 아니라 configuration binding 또는 이를 사용하는 infrastructure·maintenance 경계의 책임이다. 사용자 입력 검증과 섞지 않는다.

## 오류 응답과의 연결

| 실패 종류 | 기본 처리 |
| --- | --- |
| DTO Bean Validation | `422 VALIDATION_FAILED`, 필드 오류 포함 |
| JSON 파싱·타입 불일치 | `400 BAD_REQUEST` |
| 클라이언트가 구분할 유스케이스·도메인 실패 | 전용 `DomainException`과 `ErrorCode` |
| 일반 Entity 계약 위반 | `IllegalArgumentException` fallback 400, 구분 필요 시 전용 예외로 승격 |
| DB unique·동시성 충돌 | 의미 있는 중복 예외 또는 공통 409 |
| 예상하지 못한 시스템 실패 | 내부 상세를 로그에 남기고 안전한 500 |

Entity가 HTTP 상태를 알거나 `ResponseStatusException`을 던지지 않는다. 공개 응답 정책은 `ErrorCode`와 `GlobalExceptionHandler`가 담당한다.

## 피해야 할 패턴

- DTO annotation이 있으므로 Entity 검증을 제거
- Entity가 Repository를 주입받아 중복·존재 여부 조회
- Service마다 동일한 null·blank·길이 검사를 복사
- Controller가 Repository를 호출해 소유권과 존재 여부 판단
- DTO compact constructor에서 예외를 던져 Bean Validation 응답 흐름 우회
- Service의 `exists` 검사만 믿고 DB UNIQUE 생략
- DB constraint만 두고 정상적인 사전 오류와 제약 예외 변환 생략
- `@Column(nullable = false)`가 Java null을 즉시 막는다고 가정
- 메시지 문구 비교로 상태 전이나 HTTP 상태 결정
- 선택 입력의 `null`, blank와 삭제 의미를 정의하지 않음

## 테스트 전략

| 위치 | 테스트 내용 |
| --- | --- |
| DTO | Validator로 null·blank·경계 길이·중첩 원소 검증 |
| Entity / Value Object | factory와 change 메서드, 정규화, 상태 전이, 연관관계 불변식 |
| Policy | 유효 key, 잘못된 형식, 다른 소유자의 key 같은 경계값 |
| Service | 존재하지 않는 대상, 권한·소유권, 외부 자원 부재, 예외 타입 |
| Persistence | NOT NULL·FK·UNIQUE, orphanRemoval, ordering, optimistic lock |
| Controller / MVC | 400·422와 `ErrorResponse` 계약, `@Valid` 적용 여부 |
| 동시성 | 사전 중복 검사를 동시에 통과한 요청이 DB 제약·lock에서 하나만 성공하는지 |

## 새 검증 체크리스트

- 요청 표현만으로 판단 가능한 형식 검증인가?
- 모든 생성·변경 경로에서 지켜야 하는 Entity 불변식인가?
- 정규화를 생성과 변경에서 같은 함수로 적용하는가?
- Repository·인증 사용자·외부 상태가 필요한 Service 조건인가?
- 여러 위치에서 공유할 명확한 Policy 또는 Value Object가 필요한가?
- Service에 DTO와 Entity의 필드 검사를 그대로 복사하지 않았는가?
- 중복·참조 무결성에 DB constraint가 있는가?
- 동시성 때문에 최종 재검증이나 lock·version이 필요한가?
- 실패가 클라이언트의 분기 대상이면 전용 `DomainException`이 있는가?
- DTO·Domain·Persistence·HTTP 경계를 각각 테스트했는가?

## 면접 설명 예시

OnFilm에서는 검증 위치를 입력 출처가 아니라 판단에 필요한 정보로 나눴습니다. DTO는 null·길이 같은 API 형식을 검증해 422를 반환하고, Entity와 Value Object는 어떤 호출 경로에서도 지켜야 하는 생성·변경 불변식과 정규화를 책임집니다.

소유권, 존재 여부와 외부 파일처럼 Repository나 실행 문맥이 필요한 규칙은 Service와 Policy에 두고, 이메일 중복처럼 동시성에서 깨질 수 있는 규칙은 Service 사전 확인과 DB UNIQUE를 함께 사용했습니다. 같은 제목 길이가 DTO·Entity·DB에 보이는 것은 중복 구현이 아니라 빠른 피드백, 객체 무결성, 최종 데이터 무결성이라는 서로 다른 방어선입니다.

## 관련 문서

- [검증 흐름 컨벤션](../../convention/validation-flow-convention.md): annotation과 구현 세부 규칙
- [DTO 스타일 컨벤션](../../convention/dto-style-convention.md): 요청·응답 DTO 설계 기준
- [엔티티 리팩토링 스타일](../../convention/entity-refactoring-style-guide.md): 생성·변경 불변식과 연관관계 규칙
- [예외 정책과 오류 코드](../../convention/exception-and-error-code-convention.md): 검증 실패의 예외·응답 변환
- [DTO와 계층별 검증 흐름 정비](../../problem-solving/06-dto-and-validation-boundaries.md): 검증 리팩토링 문제 해결 사례

## 유지 규칙

검증 책임을 다른 계층으로 이동하거나 새로운 Policy·Value Object를 도입하면 관련 테스트와 이 문서를 같은 커밋에서 수정한다.
