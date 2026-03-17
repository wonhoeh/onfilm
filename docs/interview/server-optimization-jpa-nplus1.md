# 병목을 뚫는 서버 코드 최적화 가이드

이 문서는 이 프로젝트에서 서버 리소스를 아끼면서 병목을 줄인 코드 구조를 정리한 문서다.

## 면접 30초 답변 버전

“이 프로젝트에서 서버 최적화의 핵심은 서버가 꼭 해야 하는 일만 남긴 구조입니다. 조회 쪽에서는 JPA에서 `join fetch`와 `IN` 조회를 조합해 N+1 가능성을 줄였고, 읽기/쓰기를 트랜잭션 레벨에서 분리했습니다. 업로드는 presigned URL로 서버를 우회하게 만들고, 인코딩은 Kafka 비동기 작업으로 넘겨서 API 서버가 적은 리소스로도 응답성을 유지할 수 있게 설계했습니다.”

핵심 키워드:

- JPA 조회 최적화
- N+1 회피
- read/write 분리
- presigned upload
- 비동기 job 분리

## 1. JPA와 N+1 문제

이 프로젝트에서 가장 설명하기 좋은 사례는 필모그래피 조회다.

현재 구조:

- `MoviePerson`과 `Movie`는 `join fetch`
- 장르와 트레일러는 `IN` 조회
- DTO 조립 단계에서는 연관 조회를 추가로 하지 않음

의미:

- 연관 엔티티 순회 중 추가 쿼리 발생 가능성을 줄임
- 데이터가 늘어나도 쿼리 수를 예측 가능하게 유지

관련 코드:

- `src/main/java/com/onfilm/domain/movie/service/MovieReadService.java`
- `src/main/java/com/onfilm/domain/movie/repository/MoviePersonRepository.java`
- `src/main/java/com/onfilm/domain/movie/repository/MovieGenreRepository.java`
- `src/main/java/com/onfilm/domain/movie/repository/TrailerRepository.java`

면접 답변:

“필모그래피 조회에서 JPA 연관 객체를 그대로 순회하면 N+1 문제가 생길 수 있어서, `MoviePerson -> Movie`는 `join fetch`, 나머지는 `IN` 조회로 분리해서 쿼리 수를 통제했습니다. 그래서 데이터 건수가 늘어나도 쿼리 패턴이 예측 가능하도록 만들었습니다.”

## 2. 읽기/쓰기 트랜잭션 분리

현재 구조:

- 조회 서비스는 `@Transactional(readOnly = true)`
- 쓰기 메서드에만 일반 `@Transactional`

의미:

- 불필요한 dirty checking 비용 감소
- 읽기 경로와 쓰기 경로 책임 분리

관련 코드:

- `src/main/java/com/onfilm/domain/movie/service/MovieReadService.java`
- `src/main/java/com/onfilm/domain/auth/service/AuthService.java`

면접 답변:

“읽기와 쓰기를 분리해서 읽기 서비스에는 `readOnly` 트랜잭션을 적용했고, 상태 변경이 필요한 부분만 일반 트랜잭션으로 묶었습니다. 이런 분리가 결국 DB 부하를 줄이는 기본 최적화라고 생각합니다.”

## 3. 필요한 컬럼만 조회

현재 구조:

- 프로필 이미지 키 조회
- 필모그래피 파일 키 조회
- username 기반 user+person 조회

의미:

- 전체 엔티티를 다 읽지 않아도 되는 곳에서는 필요한 데이터만 조회
- 불필요한 엔티티 로딩과 직렬화 비용 감소

관련 코드:

- `src/main/java/com/onfilm/domain/movie/repository/PersonRepository.java`
- `src/main/java/com/onfilm/domain/user/repository/UserRepository.java`

## 4. 서버를 거치지 않는 업로드

현재 구조:

- 대용량 파일은 서버에 multipart 업로드하지 않음
- `S3 presigned URL` 발급 후 클라이언트가 S3로 직접 업로드

의미:

- 서버 네트워크 대역폭 절감
- 서버 메모리/I/O 사용량 절감
- 업로드 API 응답성 유지

관련 코드:

- `src/main/java/com/onfilm/domain/kafka/service/S3MediaPresignedUploadService.java`

면접 답변:

“대용량 미디어 업로드는 서버를 통과시키지 않고 presigned URL로 S3에 바로 업로드하게 해서, API 서버는 메타데이터 처리만 하도록 설계했습니다. 서버 자원을 적게 쓰면서도 업로드 응답성을 유지하기 위한 구조적 최적화였습니다.”

## 5. 무거운 작업의 비동기 분리

현재 구조:

- 업로드 완료 후 인코딩 요청은 Kafka job으로 발행
- 서버는 `jobId` 저장과 상태 관리만 담당

의미:

- 요청-응답 사이클에 무거운 인코딩 작업을 넣지 않음
- API timeout, worker 점유, CPU 병목 감소

관련 코드:

- `src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCommandService.java`

면접 답변:

“인코딩처럼 무거운 작업은 동기 요청 안에서 처리하지 않고 Kafka 비동기 작업으로 넘겼습니다. 그래서 API 서버는 사용자 응답성과 상태 관리에 집중하고, 무거운 CPU 작업은 별도 소비자에서 처리하도록 분리할 수 있었습니다.”

## 6. 추가 개선 후보

### 6-1. 외부 I/O를 트랜잭션 밖으로 이동

현재 일부 메서드는 DB 트랜잭션 안에서 S3 삭제를 호출한다.

개선 방향:

- DB 반영 후 `afterCommit` 이벤트로 파일 삭제

### 6-2. 조회 API 캐시 후보 정리

현재 Redis는 없지만, 아래는 캐시 후보가 될 수 있다.

- 공개 프로필
- 필모그래피
- 장르 목록

### 6-3. 필모그래피 API 실측

`k6`와 SQL 로그를 붙여 아래를 측정하면 면접 답변이 강해진다.

- SQL 개수
- 평균 응답 시간
- `p95`
- 데이터 증가 시 응답 시간 변화
