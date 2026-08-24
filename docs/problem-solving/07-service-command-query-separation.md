# 서비스 책임을 Command와 Query로 분리

- 작업일: 2026-08-24
- 문서 작성일: 2026-08-24
- 관련 커밋: `e1f3df3`
- 상태: 1~9단계 완료. MediaEncode 서비스의 추가 분리는 현재 보류

## 문제

`PersonReadService`라는 이름의 클래스가 프로필 조회뿐 아니라 Person·Gallery·Movie 파일 변경과 삭제 이벤트 발행까지 담당했다. `MovieService`도 Movie 생성과 Filmography 전체 교체, 공개 범위 변경을 함께 처리했다. 컨트롤러에는 소유권 판단, 공개 범위 필터링, storage key 변환, 파일 저장과 보상 삭제, Storyboard 응답 조립이 들어 있었다.

결과적으로 다음 문제가 있었다.

- Read라는 이름의 서비스에서 쓰기와 외부 부수 효과 발생
- 트랜잭션의 읽기/쓰기 경계와 테스트 대상이 불명확
- 여러 서비스에 현재 사용자 조회 코드 중복
- URL의 `publicId`를 받지만 실제 현재 사용자만 조회하여 경로 소유권을 검증하지 않는 API 존재
- 컨트롤러가 HTTP 어댑터를 넘어 도메인·스토리지 워크플로 담당
- Entity를 반환한 뒤 컨트롤러가 지연 로딩 컬렉션을 순회하며 DTO 조립

## 원인

초기 기능을 Person 중심의 소수 서비스에 계속 추가하면서 클래스 이름보다 담당 유스케이스가 빠르게 커졌다. 조회와 명령, 외부 부수 효과, API 표현 변환을 구분하는 기준이 없어 편리한 기존 서비스와 컨트롤러에 로직이 누적되었다.

## 해결

기능별로 명령과 조회 책임을 분리했다.

```text
PersonCommandService       PersonQueryService
GalleryCommandService      GalleryQueryService
MovieCommandService
FilmographyCommandService  FilmographyQueryService
StoryboardCommandService   StoryboardQueryService
UserQueryService
```

추가로 다음 경계를 만들었다.

- `CurrentPersonProvider`: 인증 사용자에서 Person 조회 및 path `publicId` 소유권 검증
- `PersonMediaService`: 프로필·갤러리·필모그래피·스토리보드 파일 워크플로
- `MovieMediaService`: Movie 썸네일·영상·Trailer 저장/삭제와 편집 권한
- `StorageFileDeletionPublisher`: 커밋 이후 삭제 이벤트 발행 공통화
- `StoryboardResponseMapper`: Entity를 API 응답 DTO와 공개 URL로 변환
- `UserQueryService`: `/auth/me`, 이메일·사용자명 사용 가능 여부 조회

기존 `PersonReadService`, `PersonService`, `MovieReadService`, `MovieService`는 제거하고 컨트롤러가 새 서비스를 사용하도록 변경했다. 공개 Gallery와 Filmography의 전체/개별 공개 범위 필터링도 Query 서비스 안에서 처리했다.

## 기술 선택과 트레이드오프

### Command/Query 분리

CQRS 인프라를 도입한 것이 아니라 메서드의 의도와 트랜잭션 성격을 클래스 수준에서 구분한 경량 구조다. 조회 서비스에는 `@Transactional(readOnly = true)`, 명령 서비스에는 쓰기 트랜잭션을 적용하기 쉬워지고 테스트 범위가 작아진다. 반면 서비스 클래스와 의존성 수가 늘고 작은 기능에서는 파일 탐색 비용이 생긴다.

### 기능 단위 서비스

Person 하나를 기준으로 거대한 서비스 두 개를 만드는 대신 Gallery, Filmography, Media처럼 변경 이유가 다른 기능으로 나눴다. 과도한 세분화를 피하기 위해 단순한 MediaEncode 유지보수 메서드는 현재 별도 서비스로 더 나누지 않았다. 분리는 코드 줄 수가 아니라 변경 이유와 트랜잭션 경계가 달라질 때 수행한다.

### 공통 CurrentPersonProvider

인증 사용자 조회와 Person 연결 검증의 중복을 제거하고 path `publicId` 불일치를 403으로 처리한다. Query에서 방문자 여부를 확인할 때는 인증 정보가 없거나 잘못된 경우 `false`로 취급한다. 편리하지만 인증 정책이 모든 도메인 서비스에 직접 퍼지지 않도록 이 컴포넌트의 책임을 Person 해석과 소유권 확인으로 제한했다.

### 미디어 워크플로 서비스

컨트롤러를 HTTP 입출력에 집중시키고 저장, 엔티티 변경, 기존 파일 삭제 예약, 실패 보상을 한 유스케이스로 묶었다. 외부 스토리지는 DB 트랜잭션에 참여하지 않으므로 완전한 원자성은 없으며, 커밋 이후 삭제와 보상 삭제 정책이 계속 필요하다.

### 전용 응답 매퍼

Storyboard Entity를 API에 직접 노출하지 않고 순서 계산과 공개 URL 변환을 한곳에 모았다. 단순 DTO의 `from()`보다 외부 `StorageService` 의존성이 있으므로 별도 컴포넌트를 선택했다. 매퍼가 Repository 조회나 상태 변경을 하지 않도록 제한한다.

## 검증

- 새 Command/Query 서비스 단위 테스트
- 현재 Person과 path publicId 불일치 시 접근 거부 테스트
- 방문자의 비공개 Gallery·Filmography 필터링 테스트
- Movie 파일 삭제 시 엔티티 참조 해제와 삭제 이벤트 발행 테스트
- PersonController validation과 새 서비스 위임 테스트
- 인증 통합 테스트를 현재 DTO 422 정책에 맞게 정비
- 전체 Gradle 테스트 190개 통과, 실패 0개, 오류 0개
- `git diff --check` 통과

## 결과

서비스 이름과 실제 동작이 일치하고, 조회·명령·외부 파일 작업의 변경 이유와 트랜잭션 경계가 분명해졌다. 컨트롤러는 HTTP 계약을 조정하고 서비스에 위임하며, 소유권과 공개 범위 정책은 재사용 가능한 서비스 계층에서 일관되게 적용된다.

## 후속 과제

- MediaEncode 유지보수 기능은 현재 복잡도가 낮아 추가 분리를 보류했다.
- 서비스 수가 늘어나는 만큼 패키지를 feature 단위로 재배치할 필요가 생기는지 관찰한다.
- 파일 삭제 실패가 운영상 누적되면 재시도 가능한 삭제 Outbox 도입을 검토한다.

## 포트폴리오 요약 후보

조회와 쓰기, 파일 부수 효과가 혼재한 대형 서비스를 기능별 Command/Query 및 Media 서비스로 분리했습니다. 공통 현재 사용자 해석과 path 소유권 검증을 도입해 무시되던 `publicId` 권한 검사를 보완하고, 컨트롤러의 공개 범위 판단·파일 보상·DTO 조립을 서비스와 전용 매퍼로 이동했습니다.
