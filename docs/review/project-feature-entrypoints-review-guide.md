# 프로젝트 기능 단위 / 엔트리포인트 / 코드 리뷰 시작 가이드

## 개요

이 문서는 `onfilm` 프로젝트를 기능 단위로 나눠서 소개하고, 각 기능의 엔트리포인트와 코드 리뷰를 어디서부터 시작하면 좋은지 정리한 문서다.

여기서 말하는 엔트리포인트는 다음 두 가지를 포함한다.

- 사용자 진입 지점: 브라우저가 처음 들어오는 URL
- 코드 진입 지점: 해당 기능에서 처음 실행되는 컨트롤러 또는 프론트 JS

---

## 1. 인증 / 세션

기능:

- 회원가입
- 로그인
- 토큰 재발급
- 내 정보 확인

백엔드 엔트리포인트:

- [`AuthController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/auth/controller/AuthController.java)

주요 API:

- `/auth/signup`
- `/auth/login`
- `/auth/refresh`
- `/auth/logout`
- `/auth/me`

프론트 엔트리포인트:

- [`login.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/login.js)
- [`signup.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/signup.js)
- [`auth.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/auth.js)

---

## 2. 공개 프로필 조회

기능:

- 배우 상세 프로필 조회
- 필모그래피 조회
- 갤러리 조회

URL 진입점:

- [`PublicProfilePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PublicProfilePageController.java)
- `/{username}` 요청을 `actor-detail.html`로 forward

실제 데이터 API:

- [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
- `/api/people/{publicId}`
- `/api/people/{publicId}/movies`
- `/api/people/{publicId}/gallery`

username -> publicId 변환 API:

- [`PersonApiController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonApiController.java)
- `/api/person/{username}`

프론트 엔트리포인트:

- [`actor-detail.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/actor-detail.js)

### publicId가 무엇인가

`publicId`는 `Person` 엔티티의 공개 조회용 UUID 문자열이다.

즉 구분은 이렇게 보면 된다.

- `id`
  - DB 내부 PK
  - `Long`
- `username`
  - 사람이 읽기 쉬운 공개 URL 식별자
  - 로그인/프로필 경로에 사용
- `publicId`
  - API 조회용 공개 식별자
  - `UUID`

관련 코드:

- [`Person.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/entity/Person.java)

### 왜 username을 바로 쓰지 않고 publicId로 변환해서 조회하는가

이 프로젝트는 공개 프로필 URL은 사람이 읽기 쉬운 `username`을 쓰고, 실제 조회 API는 `publicId`를 기준으로 동작하도록 분리해두었다.

이 구조의 의도는 다음과 같다.

- URL은 사람이 읽기 쉬워야 한다.
- 내부 DB PK(`id`)는 외부에 직접 노출하지 않는 편이 낫다.
- 조회용 식별자는 username과 분리해두면 책임이 명확해진다.

즉:

- 브라우저 주소는 `/{username}`
- 실제 API 조회는 `/api/people/{publicId}`

로 나뉜다.

### 실제 조회 흐름

공개 프로필 조회는 아래 순서로 동작한다.

1. 사용자가 `/{username}` 으로 진입
2. [`PublicProfilePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PublicProfilePageController.java)가 `actor-detail.html`로 forward
3. [`actor-detail.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/actor-detail.js)가 먼저 `/api/person/{username}` 호출
4. 서버가 `username`에 대응하는 `publicId`를 반환
5. 프론트가 반환받은 `publicId`로 아래 API를 호출
   - `/api/people/{publicId}`
   - `/api/people/{publicId}/movies`
   - `/api/people/{publicId}/gallery`

즉 공개 프로필 조회는 사실상 아래 2단계 구조다.

- `username -> publicId 변환`
- `publicId -> 상세 데이터 조회`

---

## 3. 프로필 편집

기능:

- 기본 정보 수정
- 프로필 이미지 수정
- SNS 수정
- 태그 수정

페이지 라우팅 엔트리포인트:

- [`UserPrivatePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/UserPrivatePageController.java)
- `/edit-profile`
- `/{username}/edit-profile`

데이터 API:

- [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
- `POST /api/people`
- `PUT /api/people/{publicId}`

파일 업로드 API:

- [`PersonFileController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonFileController.java)
- `/api/files/person/me/profile`

프론트 엔트리포인트:

- [`edit-profile.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/edit-profile.js)

---

## 4. 필모그래피 편집

기능:

- 작품 추가 / 수정 / 삭제
- 공개 여부 관리
- 순서 관리
- 역할 정보 관리

페이지 진입:

- `/{username}/edit-filmography`

라우팅 엔트리포인트:

- [`UserPrivatePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/UserPrivatePageController.java)

핵심 API:

- [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
- `PUT /api/people/{publicId}/filmography`
- `PUT /api/people/{publicId}/filmography/item/privacy`
- `GET /api/people/{publicId}/movies`

프론트 엔트리포인트:

- [`edit-filmography.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/edit-filmography.js)

---

## 5. 영화 / 미디어 업로드

기능:

- 썸네일 업로드 / 삭제
- 트레일러 업로드 / 삭제
- 본편 업로드 / 삭제
- 로컬 dev raw-upload

백엔드 엔트리포인트:

- [`MovieFileController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/MovieFileController.java)

핵심 API:

- `/api/files/movie/{movieId}/thumbnail/*`
- `/api/files/movie/{movieId}/trailer/*`
- `/api/files/movie/{movieId}/file/*`
- `PUT /api/files/movie/raw-upload`

프론트 엔트리포인트:

- [`edit-filmography.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/edit-filmography.js)

비고:

- 프론트에서 presign -> upload -> complete 흐름을 직접 호출한다.

---

## 6. 미디어 인코딩 파이프라인

기능:

- 업로드 완료 후 Kafka job 생성
- job 상태 조회
- 워커 콜백 반영

API 서버 job 상태 조회 엔트리포인트:

- [`MediaEncodeJobController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/controller/MediaEncodeJobController.java)
- `/api/media-jobs/{jobId}`

워커 콜백 엔트리포인트:

- [`InternalMediaCallbackController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/controller/InternalMediaCallbackController.java)

콜백 API:

- `PATCH /internal/api/media-jobs/{jobId}`
- `PATCH /internal/api/movies/{movieId}/media`
- `PATCH /internal/api/trailers/{jobId}/media`

Kafka 발행 로직:

- [`MediaEncodeJobCommandService.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCommandService.java)

---

## 7. 영상 재생

기능:

- 상세 페이지에서 플레이어 열기
- HLS 재생
- 일반 mp4 재생

공개 상세 진입:

- [`actor-detail.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/actor-detail.js)

실제 플레이어 엔트리포인트:

- [`video-player.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/video-player.js)

비고:

- `movieUrl` / `trailerUrl`을 읽어서
- HLS면 `hls.js`
- 아니면 일반 `video src`

---

## 8. 갤러리 편집

기능:

- 갤러리 이미지 업로드
- 정렬 변경
- 공개 여부 수정

데이터 API:

- [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
- `/api/people/{publicId}/gallery`
- `/api/people/{publicId}/gallery/privacy`
- `/api/people/{publicId}/gallery/item/privacy`

파일 업로드 API:

- [`PersonFileController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonFileController.java)
- `/api/files/person/me/gallery`

프론트 엔트리포인트:

- [`edit-gallery.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/edit-gallery.js)

---

## 9. 스토리보드

기능:

- 프로젝트 생성 / 수정 / 삭제
- 장면 생성 / 수정 / 삭제
- 카드 이미지 관리
- 순서 변경

페이지 진입:

- `/storyboard`
- `/edit-storyboard`
- `/storyboard-view`

라우팅 엔트리포인트:

- [`UserPrivatePageController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/UserPrivatePageController.java)

데이터 API:

- [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
- `/api/people/{publicId}/storyboard/projects...`

프론트 엔트리포인트:

- [`storyboard.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/storyboard.js)
- [`edit-storyboard.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/edit-storyboard.js)
- [`storyboard-view.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/storyboard-view.js)

---

## 10. 코드 리뷰를 시작할 때 추천 순서

이 프로젝트에서 코드 리뷰를 시작할 때는 아래 순서가 가장 좋다.

1. [`edit-filmography.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/edit-filmography.js)
2. [`PersonController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/PersonController.java)
3. [`MovieFileController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/movie/controller/MovieFileController.java)
4. [`MediaEncodeJobCommandService.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCommandService.java)
5. [`InternalMediaCallbackController.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/controller/InternalMediaCallbackController.java)
6. [`actor-detail.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/actor-detail.js)
7. [`video-player.js`](/Users/whheo/Desktop/onfilm/onfilm/src/main/resources/static/js/video-player.js)

이 순서가 좋은 이유는 다음과 같다.

- 필모그래피 편집이 프로젝트 핵심 흐름을 가장 많이 포함한다.
- 프론트 입력 -> API 저장 -> 업로드 -> Kafka -> 워커 콜백 -> 상세 조회 -> 재생까지 연결된다.
- 즉 한 흐름만 따라가도 프로젝트 전반 구조를 이해할 수 있다.

---

## 11. 한 줄 정리

이 프로젝트를 이해하려면 “필모그래피 편집 저장”을 기준으로 보면 된다.  
`edit-filmography.js -> PersonController -> MovieFileController -> MediaEncodeJobCommandService -> InternalMediaCallbackController -> actor-detail.js -> video-player.js` 흐름이 가장 핵심이다.
