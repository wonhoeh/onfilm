# Internal Media Callback API

워커가 API 서버에 인코딩 상태와 결과 경로를 반영할 때 사용하는 내부 API 문서다.

## Base

- base path: `/internal/api`

현재 서버 구현 엔드포인트:

- `PATCH /internal/api/media-jobs/{jobId}`
- `PATCH /internal/api/movies/{movieId}/media`
- `PATCH /internal/api/trailers/{jobId}/media`

## 1. Media Job Status Update

`PATCH /internal/api/media-jobs/{jobId}`

용도:

- 워커가 인코딩 작업 상태를 API 서버 DB에 반영

요청 바디:

### PROCESSING

```json
{
  "status": "PROCESSING",
  "startedAt": "2026-03-15T10:00:00Z"
}
```

### DONE

```json
{
  "status": "DONE",
  "completedAt": "2026-03-15T10:03:00Z"
}
```

### FAILED

```json
{
  "status": "FAILED",
  "failureReason": "ffmpeg exited with code 1",
  "completedAt": "2026-03-15T10:03:00Z"
}
```

상태 전이 규칙:

- `REQUESTED -> PROCESSING`
- `PROCESSING -> DONE`
- `REQUESTED -> FAILED`
- `PROCESSING -> FAILED`

허용하지 않는 전이:

- `DONE -> *`
- `FAILED -> *`
- `REQUESTED -> DONE`
- `PROCESSING -> PROCESSING`

응답:

- 성공: `204 No Content`
- 실패:
  - 없는 jobId: `404`
  - 잘못된 status 또는 필수 필드 누락: `400`
  - 잘못된 상태 전이: `400`

## 2. Movie Media Update

`PATCH /internal/api/movies/{movieId}/media`

용도:

- movie 본편 HLS 경로 또는 thumbnail 경로 반영

요청 예시:

### 비디오 경로 반영

```json
{
  "videoUrl": "movie/10/file/abc123/index.m3u8"
}
```

### 썸네일 경로 반영

```json
{
  "thumbnailUrl": "movie/10/thumbnail/abc123.jpg"
}
```

### 둘 다 반영

```json
{
  "videoUrl": "movie/10/file/abc123/index.m3u8",
  "thumbnailUrl": "movie/10/thumbnail/abc123.jpg"
}
```

응답:

- 성공: `204 No Content`
- 실패:
  - 없는 movieId: `404`
  - `videoUrl`, `thumbnailUrl` 둘 다 비어 있음: `400`

## 3. Trailer Media Update

`PATCH /internal/api/trailers/{jobId}/media`

용도:

- trailer 인코딩 결과 경로를 movie에 trailer로 반영

요청 예시:

```json
{
  "trailerUrl": "movie/10/trailer/abc123/index.m3u8"
}
```

동작:

- `jobId`로 `MediaEncodeJob`을 찾음
- 해당 job의 `jobType`이 `TRAILER`인지 확인
- `job.movieId`를 사용해 movie에 trailer URL 추가

응답:

- 성공: `204 No Content`
- 실패:
  - 없는 jobId: `404`
  - job type이 `TRAILER`가 아님: `400`
  - `trailerUrl` 누락: `400`

## 4. 권장 호출 순서

### MOVIE

1. `PATCH /internal/api/media-jobs/{jobId}` with `PROCESSING`
2. 워커 인코딩 수행
3. `PATCH /internal/api/movies/{movieId}/media` with `videoUrl`
4. `PATCH /internal/api/media-jobs/{jobId}` with `DONE`

### THUMBNAIL

1. `PATCH /internal/api/media-jobs/{jobId}` with `PROCESSING`
2. 워커 인코딩 수행
3. `PATCH /internal/api/movies/{movieId}/media` with `thumbnailUrl`
4. `PATCH /internal/api/media-jobs/{jobId}` with `DONE`

### TRAILER

1. `PATCH /internal/api/media-jobs/{jobId}` with `PROCESSING`
2. 워커 인코딩 수행
3. `PATCH /internal/api/trailers/{jobId}/media` with `trailerUrl`
4. `PATCH /internal/api/media-jobs/{jobId}` with `DONE`

### 실패

1. `PATCH /internal/api/media-jobs/{jobId}` with `PROCESSING`
2. 워커 처리 실패
3. `PATCH /internal/api/media-jobs/{jobId}` with `FAILED`

## 5. 전달용 짧은 문구

워커는 인코딩 시작 시 `PATCH /internal/api/media-jobs/{jobId}`로 `PROCESSING`을 보고하고, 성공 시 결과 경로를
`/internal/api/movies/{movieId}/media` 또는 `/internal/api/trailers/{jobId}/media`에 반영한 뒤
`/internal/api/media-jobs/{jobId}`로 `DONE`을 보고하면 됩니다. 실패 시에는 같은 상태 API로 `FAILED`,
`failureReason`, `completedAt`만 보내면 됩니다.
