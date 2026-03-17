# Context Handoff

이 문서는 현재 `onfilm` 프로젝트 작업 문맥을 다음 턴에서 빠르게 이어가기 위한 요약이다.

## 1. 최근 핵심 작업

### 1-1. 로컬(dev) producer-kafka-consumer 흐름 정리

- `dev = local`, `prod = production` 기준으로 정리함
- `application-dev.yml`
  - `file.storage.type=local`
  - `file.storage.bucket=onfilm-media`
  - `file.storage.root=/Users/whheo/Desktop/onfilm/local-storage`
- 로컬 저장 경로는:
  - `/Users/whheo/Desktop/onfilm/local-storage/onfilm-media/{targetKey}`

### 1-2. local storage bucket-aware 처리

- `LocalStorageService`
  - `root + bucket` 기준으로 실제 저장 경로 계산
- `LocalFileServeConfig`
  - `/files/**`가 bucket 포함 실제 디렉터리를 서빙하도록 수정

### 1-3. Kafka 메시지 targetKey / preset 정리

- 비동기 인코딩 targetKey 규칙:
  - movie: `movie/{movieId}/file/{uuid}/index.m3u8`
  - trailer: `movie/{movieId}/trailer/{uuid}/index.m3u8`
  - thumbnail: `movie/{movieId}/thumbnail/{uuid}.jpg`
- `EncodeJobPreset`
  - `VIDEO_HLS_720P_2500K_AAC_96K`
  - `THUMBNAIL_1280X720`
- `MovieFileController.complete*`
  - trailer/file complete 시 HLS `index.m3u8` targetKey를 발행하도록 변경

### 1-4. HLS 재생 지원

- `video-player.html`, `actor-detail.html`
  - `hls.js` CDN script 추가
- `video-player.js`
  - `m3u8`면 Safari native HLS / 그 외 브라우저는 `hls.js`
  - `movieUrl` 없으면 `trailerUrl` fallback
- `actor-detail.js`
  - hover preview video도 HLS 지원

### 1-5. 내부 콜백 API 추가

추가된 내부 API:

- `PATCH /internal/api/media-jobs/{jobId}`
- `PATCH /internal/api/movies/{movieId}/media`
- `PATCH /internal/api/trailers/{jobId}/media`

구현 위치:

- `src/main/java/com/onfilm/domain/kafka/controller/InternalMediaCallbackController.java`
- `src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobInternalService.java`

DTO:

- `MediaJobStatusUpdateRequest`
- `MovieMediaUpdateRequest`
- `TrailerMediaUpdateRequest`

### 1-6. Media job 상태 전이 가드 추가

- `MediaEncodeJob.markProcessing`
  - `REQUESTED -> PROCESSING`만 허용
- `MediaEncodeJob.markDone`
  - `PROCESSING -> DONE`만 허용
- `MediaEncodeJob.markFailed`
  - `REQUESTED|PROCESSING -> FAILED`만 허용

에러 코드:

- `INVALID_MEDIA_JOB_STATUS_TRANSITION`

### 1-7. dev presign 503 해결

문제:

- `dev`에서 S3 전용 presign 서비스만 있어서
  - `PRESIGNED_UPLOAD_NOT_CONFIGURED (503)` 발생

해결:

- `LocalMediaPresignedUploadService` 추가
- `MovieFileController`
  - `PUT /api/files/movie/raw-upload?sourceKey=...` 추가
- `edit-filmography.js`
  - 로컬 업로드 URL에 대해 CSRF 헤더 + credentials 포함해서 업로드하도록 수정

### 1-8. 프로필 이미지 경로 처리 보강

- `LocalStorageService.toPublicUrl()`
- `S3StorageService.toPublicUrl()`

보강 내용:

- DB에 이미 절대 URL이 있으면 그대로 반환
- `/files/...` 형태면 그대로 반환
- key면 public URL로 변환

의도:

- 예전 URL 저장 데이터와 새 key 저장 데이터 혼재 시에도 깨지지 않게 함

## 2. CSRF / internal API 관련 작업

### 2-1. 원인 분석

워커가 `/internal/api/media-jobs/{jobId}` 호출 시 403 발생 원인은:

- 인증 문제가 아니라 `CsrfProtectionFilter`
- 워커는 브라우저가 아니므로
  - `Origin/Referer`
  - `XSRF-TOKEN` cookie
  - `X-CSRF-TOKEN` header
  를 보내지 않음

### 2-2. 문서 작성

작성된 문서:

- `docs/csrf-internal-api-analysis.md`
- `docs/internal-api-token-design.md`
- `docs/internal-media-callback-api.md`
- `docs/local-producer-consumer-setup.md`

### 2-3. dev / prod CSRF 분리

현재 수정:

- `CsrfProtectionFilter`
  - `@Profile("prod")`
- `DevCsrfProtectionFilter`
  - `@Profile("dev")`
  - `/internal/api/**`는 CSRF 검사 제외

구현 위치:

- `src/main/java/com/onfilm/domain/common/config/CsrfProtectionFilter.java`
- `src/main/java/com/onfilm/domain/common/config/DevCsrfProtectionFilter.java`

주의:

- `SecurityConfig`는 현재
  - dev 체인: `@Profile("dev")`
  - prod 체인: `@Profile("!dev")`
- 따라서 `prod` 외 다른 프로필(`staging` 등)이 생기면 CSRF 필터 profile과 security chain profile 조합을 다시 정리해야 함

## 3. 면접/문서 관련 산출물

분리된 문서:

- `docs/interview/k6-guide.md`
- `docs/interview/db-index-guide.md`
- `docs/interview/transaction-lock-strategy.md`
- `docs/interview/server-optimization-jpa-nplus1.md`

각 문서에는 `면접 30초 답변 버전` 포함됨

## 4. 현재 남아 있는 확인/후속 작업

### 4-1. 서버 재시작 후 재검증 필요

아래는 코드 반영 후 실제 런타임 검증이 아직 필요함:

- dev에서 필모 편집 저장 전체 흐름
- worker -> `/internal/api/**` callback 403 해소 여부
- HLS 결과 생성 후 브라우저 재생
- 프로필 이미지 업로드 후 상세 페이지 반영

### 4-2. prod 보안 강화 미구현

문서만 있고 실제 코드는 아직 안 붙은 것:

- `/internal/api/**`용 `X-INTERNAL-API-KEY` 필터
- worker가 내부 토큰 헤더를 붙이도록 변경

### 4-3. compile/test 미실행

이 환경에서는 Gradle wrapper 다운로드가 네트워크 제약으로 막혀서 `./gradlew compileJava` 검증을 완료하지 못함

시도 결과:

- 전역 gradle cache 접근 제한
- `/tmp`로 gradle home 변경 후에도 `services.gradle.org` 다운로드 실패

## 5. 다음 턴에서 우선 볼 것

1. 서버 재시작 후 worker callback 403이 실제로 해결됐는지 확인
2. 아직 403이면 security chain / filter bean 등록 상태 점검
3. 필요 시 `/internal/api/**`용 internal token filter 실제 구현
4. dev에서 필모 업로드와 프로필 이미지 반영을 브라우저 기준으로 재검증
