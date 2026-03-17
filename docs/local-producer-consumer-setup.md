# Local Producer-Kafka-Consumer Setup

이 문서는 API 서버 프로듀서와 로컬 Kafka, 로컬 워커 컨슈머를 함께 테스트하기 위한 설정 기준을 정리한다.

## Profiles

- `dev`: 로컬 E2E 테스트용
- `prod`: 운영용

## Storage Rules

워커는 메시지의 `targetBucket`, `targetKey`를 그대로 사용한다.

- `dev`
  - root: `/Users/whheo/Desktop/onfilm/local-storage`
  - bucket: `onfilm-media`
  - physical path: `/Users/whheo/Desktop/onfilm/local-storage/onfilm-media/{targetKey}`
- `prod`
  - physical path: `s3://{targetBucket}/{targetKey}`

API 서버 DB에는 물리 경로가 아니라 `targetKey`만 저장하는 전제를 유지한다.

## Producer Message Rules

비동기 인코딩 요청의 target key 는 아래 규칙을 사용한다.

- `MOVIE`: `movie/{movieId}/file/{uuid}/index.m3u8`
- `TRAILER`: `movie/{movieId}/trailer/{uuid}/index.m3u8`
- `THUMBNAIL`: `movie/{movieId}/thumbnail/{uuid}.jpg`

비디오 preset 은 아래로 통일한다.

- `VIDEO_HLS_720P_2500K_AAC_96K`

## Dev Profile

`application-dev.yml`

- Kafka: `localhost:9092`
- storage type: `local`
- storage root: `/Users/whheo/Desktop/onfilm/local-storage`
- storage bucket: `onfilm-media`
- public base url: `http://localhost:8080/files`

개발 파일 서빙도 bucket 경로를 포함한 실제 저장 위치를 기준으로 맞춘다. 따라서 워커가
`/Users/whheo/Desktop/onfilm/local-storage/onfilm-media/{targetKey}`에 저장한 파일은 API 서버에서
`/files/{targetKey}` URL로 접근할 수 있다.

## Local Test Flow

현재 API 서버는 `dev` 프로필에서 S3 presign 대체 구현까지 포함하지는 않는다. 따라서 로컬 E2E 테스트는 아래 순서가 가장 단순하다.

1. 원본 파일을 `/Users/whheo/Desktop/onfilm/local-storage/onfilm-media/{sourceKey}` 위치에 준비
2. API 서버의 `complete` 엔드포인트 호출
3. 서버가 Kafka 메시지를 발행
4. 로컬 워커가 consume 후 `targetKey` 기준으로 결과 저장
5. 워커가 API 서버에 상태 반영

예시:

- source: `/Users/whheo/Desktop/onfilm/local-storage/onfilm-media/movie/10/raw/file/input.mp4`
- request:

```json
{
  "sourceKey": "movie/10/raw/file/input.mp4",
  "contentType": "video/mp4"
}
```

- produced target:
  - `movie/10/file/{uuid}/index.m3u8`

## Notes

- HLS 는 `index.m3u8`만 저장되는 것이 아니라 같은 디렉터리에 `segment_*.ts`가 함께 생성된다.
- direct upload 동기 경로는 기존 mp4/jpg 저장 로직을 유지하고, Kafka 기반 비동기 경로만 HLS target key 규칙으로 맞춘다.
