# Service 단일 책임 지도

- 기준일: 2026-08-25
- 대상: `src/main/java`의 `*Service` 36개(인터페이스와 환경별 구현 포함)
- 관련 리팩토링: `e1f3df3`, `7b8cf7f`, `16bca90`, `827a860`, `e11eae9`, `5808bdb`
- 상태: 현재 코드 기준 완료

## 목적

새 기능을 어느 서비스에 배치할지 판단하고, 이름이 비슷한 Command·Query·Media·Transaction 서비스의 경계를 빠르게 확인하기 위한 문서다.

단일 책임은 **메서드가 하나여야 한다는 뜻이 아니라, 클래스를 변경하게 만드는 이유가 하나여야 한다는 뜻**으로 사용한다. 같은 Aggregate를 다루더라도 조회 정책, 상태 변경, 외부 I/O, 트랜잭션 원자성의 변경 이유가 다르면 서비스를 나눈다.

## 이름으로 판단하는 기본 규칙

| 이름 | 책임 | 기본 경계 |
| --- | --- | --- |
| `*QueryService` | 조회, 공개 범위 적용, 응답 projection 조립 | `@Transactional(readOnly = true)` |
| `*CommandService` | 유스케이스 단위의 도메인 상태 변경 | 쓰기 트랜잭션 |
| `*MediaService` | 파일 저장·인코딩·URL 변환과 실패 보상 등 외부 I/O 흐름 조정 | DB 트랜잭션을 소유하지 않음 |
| `*TransactionService` | 짧은 DB 조회·변경과 최종 상태 재검증 | 메서드별 읽기/쓰기 트랜잭션 |
| `*MaintenanceService`, `*CleanupService` | 예약 실행되는 보존·정리·timeout 정책 | 작업 단위 쓰기 트랜잭션 |
| 인터페이스 `*Service` | 애플리케이션이 필요로 하는 외부 기능의 port | 구현 기술을 노출하지 않음 |
| `Local*Service`, `S3*Service` | port의 환경별 adapter | 로컬 또는 S3 API 호출만 담당 |

## 인증·사용자 서비스

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`AuthService`](../../../src/main/java/com/onfilm/domain/auth/service/AuthService.java) | 회원가입·로그인·토큰 갱신·로그아웃이라는 인증 유스케이스 조정 | BCrypt와 JWT·Refresh Token 흐름을 조정한다. 가입·로그인의 DB 원자성은 `AuthTransactionService`에 위임한다. |
| [`AuthTransactionService`](../../../src/main/java/com/onfilm/domain/auth/service/AuthTransactionService.java) | 인증에 필요한 User DB 작업을 짧은 트랜잭션으로 수행 | 가입 가능 여부와 로그인 snapshot 조회, User·Person 저장, DB unique 경쟁 조건 변환을 담당한다. BCrypt와 JWT 생성은 하지 않는다. |
| [`UserQueryService`](../../../src/main/java/com/onfilm/domain/user/service/UserQueryService.java) | 인증 사용자 정보와 계정 식별자 사용 가능 여부 조회 | User 상태를 변경하거나 가입을 수행하지 않는다. `MeResponse` 조립과 이메일·사용자명 중복 조회만 담당한다. |

## Refresh Token 서비스

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`RefreshTokenService`](../../../src/main/java/com/onfilm/domain/token/service/RefreshTokenService.java) | Refresh Token의 발급·회전·폐기 생명주기 관리 | 원문 대신 해시를 저장하고 낙관적 락과 재사용 감지를 적용한다. 예외 반환 뒤에도 보존할 보안 변경은 별도 서비스에 위임한다. |
| [`RefreshTokenSecurityTransactionService`](../../../src/main/java/com/onfilm/domain/token/service/RefreshTokenSecurityTransactionService.java) | 외부 인증 실패가 롤백돼도 남아야 하는 보안 상태를 독립 커밋 | 만료 토큰 폐기와 재사용 감지 시 사용자 세션 삭제만 `REQUIRES_NEW`로 수행한다. 일반 토큰 흐름의 기본 트랜잭션으로 사용하지 않는다. |
| [`RefreshTokenCleanupService`](../../../src/main/java/com/onfilm/domain/token/service/RefreshTokenCleanupService.java) | 보존 기간이 지난 만료·폐기 Refresh Token 정리 | 스케줄에 따라 오래된 기록을 삭제한다. 토큰 발급·검증·회전은 하지 않는다. |

## Person·Gallery·Filmography·Storyboard 서비스

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`PersonCommandService`](../../../src/main/java/com/onfilm/domain/movie/service/PersonCommandService.java) | Person 프로필 상태 생성·수정 | 기본 정보·SNS·태그를 Aggregate 메서드로 반영한다. 프로필 조회와 파일 저장은 하지 않는다. |
| [`PersonQueryService`](../../../src/main/java/com/onfilm/domain/movie/service/PersonQueryService.java) | Person 공개 프로필과 공개 파일 위치 조회 | 조회 결과를 DTO 또는 공개 URL로 변환한다. Person 상태를 변경하지 않는다. |
| [`GalleryCommandService`](../../../src/main/java/com/onfilm/domain/movie/service/GalleryCommandService.java) | Gallery 항목·순서·공개 범위 변경 | Person의 Gallery 상태를 변경하고 제거 파일의 커밋 후 삭제를 예약한다. 신규 파일 저장은 `PersonMediaService` 책임이다. |
| [`GalleryQueryService`](../../../src/main/java/com/onfilm/domain/movie/service/GalleryQueryService.java) | 소유자 여부와 공개 정책을 적용한 Gallery 조회 | 비공개 Gallery와 항목을 필터링하고 storage key를 공개 URL로 변환한다. |
| [`FilmographyCommandService`](../../../src/main/java/com/onfilm/domain/movie/service/FilmographyCommandService.java) | Person의 Filmography 구성 전체와 공개 범위 변경 | Movie·MoviePerson·Genre 관계를 생성·재사용·제거·정렬한다. 카드 조회와 영상 파일 처리는 하지 않는다. |
| [`FilmographyQueryService`](../../../src/main/java/com/onfilm/domain/movie/service/FilmographyQueryService.java) | 공개 정책이 적용된 Filmography 카드 조회 | MoviePerson·Genre·Trailer를 일괄 조회해 응답을 조립한다. Filmography 상태는 변경하지 않는다. |
| [`StoryboardCommandService`](../../../src/main/java/com/onfilm/domain/movie/service/StoryboardCommandService.java) | Storyboard Project·Scene·Card 생명주기와 순서 변경 | 소유권과 image key를 검증하고 삭제 대상 파일을 커밋 후 삭제로 예약한다. 이미지 업로드 자체는 하지 않는다. |
| [`StoryboardQueryService`](../../../src/main/java/com/onfilm/domain/movie/service/StoryboardQueryService.java) | 소유자의 Storyboard 목록·상세 조회와 응답 조립 | Project preview와 Scene·Card 응답을 만든다. Storyboard 상태 변경은 하지 않는다. |

## Movie·미디어 서비스

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`MovieCommandService`](../../../src/main/java/com/onfilm/domain/movie/service/MovieCommandService.java) | 현재 Person의 Movie와 참여 관계 생성 | Movie·MoviePerson·MovieGenre의 초기 상태와 정렬 순서를 한 트랜잭션에서 만든다. 기존 Filmography 전체 교체는 담당하지 않는다. |
| [`PersonMediaService`](../../../src/main/java/com/onfilm/domain/movie/service/PersonMediaService.java) | Person 소유 파일의 저장·공개 URL 변환·실패 보상 조정 | 스토리지 I/O를 DB 트랜잭션 밖에서 수행하고 최종 DB 변경은 `PersonMediaTransactionService`에 위임한다. |
| [`PersonMediaTransactionService`](../../../src/main/java/com/onfilm/domain/movie/service/PersonMediaTransactionService.java) | Person 미디어 참조를 짧은 DB 트랜잭션에서 변경 | 최종 소유권을 다시 확인하고 기존 key의 커밋 후 삭제를 예약한다. 파일 저장·삭제 API를 직접 호출하지 않는다. |
| [`MovieMediaService`](../../../src/main/java/com/onfilm/domain/movie/service/MovieMediaService.java) | Movie 파일의 인코딩·저장·실패 보상 조정 | ffmpeg와 스토리지 I/O를 트랜잭션 밖에서 실행한다. Movie 상태 반영은 `MovieMediaTransactionService`에 위임한다. |
| [`MovieMediaTransactionService`](../../../src/main/java/com/onfilm/domain/movie/service/MovieMediaTransactionService.java) | Movie 미디어 참조를 짧은 DB 트랜잭션에서 변경 | 편집 권한과 storage key를 최종 재검증하고 기존 파일의 커밋 후 삭제를 예약한다. 인코딩과 저장은 하지 않는다. |
| [`GenreService`](../../../src/main/java/com/onfilm/domain/genre/service/GenreService.java) | 활성 표준 Genre 자동완성 조회 | 입력을 정규화하고 최대 10개를 조회한다. Genre 생성·수정 기능이 없어 현재 Command/Query로 추가 분리하지 않는다. |

## 파일 port와 adapter

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`StorageService`](../../../src/main/java/com/onfilm/domain/file/service/StorageService.java) | storage key 기반 파일 저장소 기능 정의 | 저장·삭제·존재 확인·공개 URL 변환이라는 애플리케이션 port다. Local/S3 세부 구현을 노출하지 않는다. |
| [`LocalStorageService`](../../../src/main/java/com/onfilm/domain/file/infrastructure/local/LocalStorageService.java) | `StorageService`의 로컬 파일 시스템 구현 | 로컬 경로 안전성과 파일 I/O만 담당한다. 도메인 소유권은 판단하지 않는다. |
| [`S3StorageService`](../../../src/main/java/com/onfilm/domain/file/infrastructure/s3/S3StorageService.java) | `StorageService`의 S3 구현 | S3 객체 저장·삭제·HEAD와 공개 URL 생성을 담당한다. 도메인 상태를 변경하지 않는다. |
| [`MediaEncodingService`](../../../src/main/java/com/onfilm/domain/file/service/MediaEncodingService.java) | 영상·이미지 인코딩 기능 정의 | 입력·출력 `Path`와 인코딩 조건만 정의하는 port다. Movie나 Person을 알지 않는다. |
| [`LocalMediaEncodingService`](../../../src/main/java/com/onfilm/domain/file/infrastructure/local/LocalMediaEncodingService.java) | `MediaEncodingService`의 로컬 ffmpeg 구현 | 프로세스 실행, 결과 파일 생성과 실패 변환을 담당한다. 저장소 업로드와 DB 반영은 하지 않는다. |

## Presigned upload 서비스

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`MediaPresignedUploadService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaPresignedUploadService.java) | 업로드 위치와 만료 시각 발급 기능 정의 | local/S3 차이를 숨기는 port다. 업로드 요청의 DB 생명주기는 관리하지 않는다. |
| [`LocalMediaPresignedUploadService`](../../../src/main/java/com/onfilm/domain/kafka/service/LocalMediaPresignedUploadService.java) | 로컬 환경용 raw upload URL 생성 | 서버의 로컬 업로드 endpoint와 만료 시각을 반환한다. 요청을 DB에 저장하지 않는다. |
| [`S3MediaPresignedUploadService`](../../../src/main/java/com/onfilm/domain/kafka/service/S3MediaPresignedUploadService.java) | S3 PUT presigned URL 생성 | bucket·key·content type과 서명 만료를 S3 요청으로 변환한다. 업로드 완료 여부는 판단하지 않는다. |
| [`MediaUploadRequestService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaUploadRequestService.java) | presign 업로드 요청의 발급·인가 생명주기 관리 | `requestId`와 발급 조건을 DB에 저장하고 로컬 raw upload를 인가한다. 인코딩 Job 생성은 하지 않는다. |

## Media Encode Job·Outbox 서비스

| Service | 하나의 책임 | 경계와 제외 대상 |
| --- | --- | --- |
| [`MediaEncodeJobCommandService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCommandService.java) | 인코딩 요청 유스케이스 조정 | 기존 Job 확인, key 정책, 원본 존재 확인을 거쳐 최종 생성 트랜잭션을 호출한다. S3 HEAD를 DB 잠금 안에서 수행하지 않는다. |
| [`MediaEncodeJobTransactionService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobTransactionService.java) | 업로드 완료와 Job·Outbox 생성을 원자적으로 반영 | 잠금 후 상태를 재검증하고 Job과 Outbox를 같은 트랜잭션에 저장한다. Kafka를 직접 호출하지 않는다. |
| [`MediaEncodeJobQueryService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobQueryService.java) | 요청 사용자가 소유한 인코딩 Job 상태 조회 | 클라이언트 polling 응답만 제공한다. Job 상태를 변경하지 않는다. |
| [`MediaEncodeJobInternalService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobInternalService.java) | Worker Callback에 따른 Job 처리·완료·실패 흐름 조정 | PROCESSING·FAILED 전이를 처리하고, 완료 시 결과 파일 존재 확인을 트랜잭션 밖에서 수행한 뒤 최종 반영을 위임한다. |
| [`MediaEncodeJobCompletionTransactionService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCompletionTransactionService.java) | Callback 결과와 Movie 반영, Job 완료를 원자적으로 처리 | Callback 정보와 Job 상태를 최종 재검증하고 Movie key 변경과 `DONE` 전이를 함께 커밋한다. S3 존재 확인은 하지 않는다. |
| [`MediaEncodeOutboxTransactionService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeOutboxTransactionService.java) | Outbox 선점·발행 성공·실패 상태를 독립 트랜잭션으로 기록 | lease를 가진 발행 단위를 `REQUIRES_NEW`로 커밋한다. Kafka 전송 자체는 Publisher 책임이다. |
| [`MediaEncodeMaintenanceService`](../../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeMaintenanceService.java) | 미디어 인코딩 관련 운영 데이터의 시간 기반 생명주기 관리 | timeout Job 상태 전이와 보존 기간이 지난 Job·Outbox·UploadRequest 정리를 스케줄로 수행한다. 사용자 요청을 처리하지 않는다. |

## 서비스가 아닌 주요 협력 컴포넌트

이 컴포넌트들은 서비스가 자신의 책임을 넘지 않도록 공통 정책이나 변환을 맡는다.

| Component | 책임 |
| --- | --- |
| `CurrentPersonProvider` | 인증 사용자 ID를 Person으로 해석하고 path `publicId` 소유권 검증 |
| `StorageKeyFactory` | 용도와 소유자별 새로운 storage key 생성 |
| `StorageKeyPolicy` | storage key 형식과 Person·Movie 소유권 검증 |
| `StorageFileDeletionPublisher` | 활성 트랜잭션에서 커밋 후 파일 삭제 이벤트 발행 |
| `MovieGenreNormalizer` | 표준·사용자 입력 Genre 정규화와 Movie 연결 |
| `StoryboardResponseMapper` | Storyboard Entity를 API 응답과 공개 URL로 변환 |
| `MediaEncodeOutboxPublisher` | 커밋된 Outbox payload를 Kafka로 전송하고 결과 상태 기록 |

## 새 로직 배치 판단표

| 새 요구사항 | 배치 위치 |
| --- | --- |
| 화면에 필요한 데이터 조회와 공개 범위 필터링 | 해당 `QueryService` |
| Aggregate 상태 생성·변경·삭제 | 해당 `CommandService` |
| S3·파일 시스템·ffmpeg 같은 외부 I/O | 비트랜잭션 `MediaService` 또는 infrastructure adapter |
| 외부 I/O 후 최종 DB 반영과 재검증 | 해당 `TransactionService` |
| 반드시 함께 커밋되어야 하는 여러 DB 변경 | 하나의 `TransactionService` 메서드 |
| 실패 응답과 무관하게 독립 보존할 보안 기록 | 제한적인 `REQUIRES_NEW` 서비스 |
| 주기적인 timeout·보존 기간 정리 | `MaintenanceService` 또는 `CleanupService` |
| 여러 서비스에서 공유하는 순수 형식·소유권 정책 | 전용 Policy·Provider·Mapper 컴포넌트 |

## 책임 변경 체크리스트

- 서비스 이름만 보고 조회·변경·외부 I/O 여부를 예측할 수 있는가?
- 추가하려는 로직이 기존 서비스와 같은 이유로 변경되는가?
- Query 서비스가 엔티티 상태를 변경하거나 외부 부수 효과를 만들지 않는가?
- 비트랜잭션 오케스트레이터가 영속 Entity를 트랜잭션 밖으로 전달받지 않는가?
- 외부 I/O 뒤 쓰기 트랜잭션에서 권한과 상태를 다시 검증하는가?
- `TransactionService`가 외부 스토리지나 느린 CPU 작업을 직접 호출하지 않는가?
- 환경별 구현 차이는 port와 adapter 뒤에 숨겨져 있는가?
- 단순히 클래스가 길다는 이유가 아니라 변경 이유가 달라질 때 분리하는가?

## 문서 유지 규칙

`*Service`를 추가·삭제하거나 책임을 이동하는 리팩토링에서는 이 문서를 같은 커밋에서 수정한다. 코드와 설명이 다르면 코드가 현재 동작의 기준이며, 문서는 그 차이를 발견한 작업에서 즉시 갱신한다.
