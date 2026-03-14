# Consumer Development Spec

## Goal

프로듀서는 업로드 완료 후 Kafka로 인코딩 요청 메시지를 발행한다. 컨슈머는 이 메시지를 consume해서 인코딩, 결과 저장, DB 상태 반영, 최종 미디어 경로 갱신까지 처리한다.

## Current Producer Status

- 구현 완료 범위: presign API, S3 direct upload, complete API, Kafka publish, `jobId` 반환
- Kafka 토픽: `media.encode.requested`
- Kafka message key: `jobId`
- complete API 호출 시 서버는 `MediaEncodeJob`을 `REQUESTED` 상태로 먼저 저장한 뒤 Kafka에 메시지를 발행함
- 프론트는 `GET /api/media-jobs/{jobId}` polling 으로 상태를 조회함

## Kafka Message Schema

```json
{
  "jobId": "uuid",
  "movieId": 123,
  "requestedByUserId": 45,
  "jobType": "MOVIE | TRAILER | THUMBNAIL",
  "preset": "VIDEO_HLS_720P_2500K_AAC_96K | THUMBNAIL_1280X720",
  "sourceBucket": "s3-bucket",
  "sourceKey": "movie/123/raw/file/uuid.mp4",
  "targetBucket": "s3-bucket",
  "targetKey": "movie/123/file/uuid/index.m3u8",
  "contentType": "video/mp4",
  "requestedAt": "2026-03-15T00:00:00Z"
}
```

## Cost-Optimized Encoding Policy

- 비디오는 단일 rendition만 사용
- HLS + H.264 + AAC 사용
- 해상도: `1280x720`
- 비디오 bitrate: `2500k`
- 오디오 bitrate: `96k`
- 오디오 채널: `2`
- 오디오 샘플레이트: `48000`
- HLS segment duration: `6`
- segment 포맷: `.ts`
- 썸네일은 `1280x720 jpg`

## Preset Design

```java
public enum EncodeJobPreset {
    VIDEO_HLS_720P_2500K_AAC_96K,
    THUMBNAIL_1280X720
}
```

- `jobType`은 작업 대상 구분용
- `preset`은 실제 인코딩 규격 구분용
- `MOVIE`와 `TRAILER`는 같은 비디오 preset 사용

매핑:

- `MOVIE` + `VIDEO_HLS_720P_2500K_AAC_96K`
- `TRAILER` + `VIDEO_HLS_720P_2500K_AAC_96K`
- `THUMBNAIL` + `THUMBNAIL_1280X720`

## Target Key Design

HLS는 산출물이 여러 개이므로 `targetKey`는 단일 mp4 파일 경로가 아니라 manifest 기준 경로로 사용한다.

규칙:

- movie: `movie/{movieId}/file/{uuid}/index.m3u8`
- trailer: `movie/{movieId}/trailer/{uuid}/index.m3u8`
- thumbnail: `movie/{movieId}/thumbnail/{uuid}.jpg`

예시:

- `movie/123/file/550e8400-e29b-41d4-a716-446655440000/index.m3u8`
- `movie/123/file/550e8400-e29b-41d4-a716-446655440000/segment_000.ts`
- `movie/123/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8`
- `movie/123/thumbnail/550e8400-e29b-41d4-a716-446655440000.jpg`

컨슈머 규칙:

- `targetKey`는 최종 manifest 경로로 해석
- segment 파일은 `targetKey`의 부모 디렉터리에 함께 저장

## Consumer Responsibilities

1. Kafka 메시지를 consume한다.
2. `jobId`로 `MediaEncodeJob.status`를 `PROCESSING`으로 변경하고 `startedAt`을 기록한다.
3. `sourceBucket/sourceKey`에서 원본 파일을 가져온다.
4. 아래 규칙으로 인코딩한다.
5. 결과물을 `targetBucket/targetKey` 기준으로 저장한다.
6. 성공 시 `MediaEncodeJob.status = DONE`, `completedAt` 저장
7. 실패 시 `MediaEncodeJob.status = FAILED`, `failureReason`, `completedAt` 저장
8. 성공 시 실제 서비스 DB의 미디어 경로도 갱신한다.

비디오 처리:

- 조건: `jobType = MOVIE | TRAILER`
- 조건: `preset = VIDEO_HLS_720P_2500K_AAC_96K`
- 출력: 단일 rendition HLS
- 대표 파일: `index.m3u8`

썸네일 처리:

- 조건: `jobType = THUMBNAIL`
- 조건: `preset = THUMBNAIL_1280X720`
- 출력: `1280x720 jpg`

## DB Update Rules After Success

- `MOVIE` 성공 시 movie의 video URL 을 `targetKey`로 갱신
- `TRAILER` 성공 시 trailer 엔티티에 `targetKey` 추가
- `THUMBNAIL` 성공 시 movie thumbnail URL 을 `targetKey`로 갱신

주의:

- 현재 비동기 경로에서는 job 상태 조회 API는 존재하지만, 인코딩 성공 후 실제 movie/trailer/thumbnail 엔티티 갱신은 아직 구현되지 않음
- 이 부분은 컨슈머 또는 컨슈머가 호출하는 서버 서비스에서 반드시 처리해야 함

## Frontend Completion Condition

- 프론트는 `GET /api/media-jobs/{jobId}`를 polling 한다
- 응답 상태가 `DONE`이면 업로드 완료로 간주하고 화면 갱신
- 응답 상태가 `FAILED`이면 실패 처리 및 에러 메시지 노출

## Final Agreement

- 기존 `MOVIE_720P_3000K`, `TRAILER_720P_3000K` preset은 사용하지 않음
- 비디오 preset은 `VIDEO_HLS_720P_2500K_AAC_96K`로 통합
- 기존 비디오 `targetKey`의 `.mp4` 파일 경로 설계는 폐기
- 비디오 `targetKey`는 반드시 HLS manifest 경로인 `.../index.m3u8`로 생성
