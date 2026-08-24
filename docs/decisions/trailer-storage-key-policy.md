# Trailer 저장 위치는 URL 대신 storageKey를 사용한다

- 상태: Accepted
- 결정일: 2026-08-19

## 배경

OnFilm은 Trailer 파일을 로컬 스토리지 또는 S3에 저장한다. 서버가 발급하는 Trailer 식별자는 다음과 같은 상대 경로다.

```text
movie/{movieId}/trailer/{UUID}.mp4
movie/{movieId}/trailer/{UUID}/index.m3u8
```

기존 `Trailer.url` 필드는 이름과 달리 이 storageKey를 저장하면서도 완전한 HTTP URL을 거부하지 않았다. 이 상태에서는 값의 의미가 불분명하고, 파일 삭제와 Movie 소유권 검증을 안전하게 수행하기 어렵다.

## 결정

Trailer 엔티티에는 OnFilm이 소유한 파일의 `storageKey`만 저장한다.

- DB 컬럼: `trailer.storage_key`
- 엔티티 필드: `Trailer.storageKey`
- 내부 인코딩 콜백 입력: `trailerKey`
- 공개 조회 응답: `trailerUrl`

입력 키는 서버 발급 형식의 하나와 정확히 일치해야 한다.

```text
movie/{movieId}/trailer/{UUID}.{extension}
movie/{movieId}/trailer/{UUID}/index.m3u8
```

키에 포함된 `movieId`는 변경 대상 Movie ID와 같아야 한다. HTTP URL, 절대 경로, 역슬래시, 경로 탐색, 다른 Movie나 다른 파일 종류의 키는 거부한다.

Movie 생성 시점에는 아직 Movie ID가 없으므로 생성 요청에서 Trailer를 함께 받지 않는다. Movie를 먼저 저장한 다음 발급된 ID로 업로드 키를 만들고, 업로드 또는 인코딩 완료 후 Trailer를 등록한다.

클라이언트에 반환할 때는 다음 경계를 적용한다.

```text
DB storageKey
  -> StorageService.toPublicUrl(storageKey)
  -> API trailerUrl
```

## 선택 이유

- 로컬, S3, CDN 환경이 바뀌어도 DB 값을 변경하지 않아도 된다.
- 키 경로의 Movie ID로 파일 소유권을 검증할 수 있다.
- 파일 삭제 API에 DB 값을 그대로 안전하게 전달할 수 있다.
- CDN 도메인과 공개 URL 정책이 도메인 엔티티에 유입되지 않는다.
- 외부 URL을 우리 소유 파일로 오인해 삭제하려는 문제를 방지한다.

## 트레이드오프

### 얻는 점

- 저장 값의 의미가 하나로 고정된다.
- 환경별 공개 주소와 영속 데이터를 분리한다.
- 임의 URL과 다른 Movie의 키를 차단할 수 있다.
- 파일 정리와 트랜잭션 커밋 후 삭제 흐름을 일관되게 유지할 수 있다.

### 감수하는 점

- 조회 응답을 만들 때마다 storageKey를 공개 URL로 변환해야 한다.
- YouTube나 Vimeo 같은 외부 Trailer URL을 현재 모델에 직접 저장할 수 없다.
- 기존 `url` 컬럼과 `trailerUrl` 콜백 계약을 사용하는 데이터·클라이언트는 마이그레이션해야 한다.
- Movie 생성과 Trailer 등록이 하나의 요청이 아니라 두 단계가 된다.

## 검토했지만 선택하지 않은 대안

### 완전한 URL 저장

클라이언트가 즉시 사용할 수 있지만 CDN 도메인과 실행 환경이 DB 데이터에 결합된다. 우리 파일과 외부 파일을 구분하기 어렵고 소유권 검증 및 삭제가 불안전해져 선택하지 않았다.

### URL과 storageKey를 한 문자열 필드에 혼용

기존 클라이언트와 호환하기 쉽지만 모든 사용 지점에서 문자열 접두어로 종류를 추측해야 한다. 잘못된 삭제와 검증 우회를 만들 수 있어 선택하지 않았다.

### 외부 URL과 storageKey를 동시에 모델링

외부 동영상 지원이 실제 요구사항이 되면 다음처럼 명시적인 출처 모델을 추가한다.

```text
TrailerSourceType.STORAGE + storageKey
TrailerSourceType.EXTERNAL + externalUrl
```

현재는 외부 Trailer 요구사항이 없으므로 불필요한 상태와 분기를 추가하지 않는다.

## 적용 및 마이그레이션 유의사항

- 기존 `trailer.url` 컬럼은 `storage_key`로 변경한다.
- 기존 값 중 `http://` 또는 `https://`로 시작하는 값이 있다면 자동으로 storageKey로 간주하지 않는다.
- 외부 URL 데이터는 삭제하거나 OnFilm 스토리지로 이전한 뒤 새 storageKey로 교체해야 한다.
- 내부 미디어 워커는 콜백 필드를 `trailerUrl`에서 `trailerKey`로 변경해야 한다.
- API의 공개 응답 필드 `trailerUrl`은 유지되며 실제 접근 가능한 URL을 반환한다.
