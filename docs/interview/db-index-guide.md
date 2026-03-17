# DB 인덱스와 쿼리 단축 가이드

이 프로젝트에서는 “인덱스만 붙여서 빨라졌다”보다 “조회 패턴을 줄이고 필요한 인덱스를 설계했다”라고 설명하는 편이 정확하다.

## 면접 30초 답변 버전

“이 프로젝트에서는 인덱스만 추가하기보다 조회 패턴 자체를 줄이는 쪽으로 접근했습니다. 필모그래피 조회에서 `MoviePerson`과 `Movie`는 `join fetch`, 장르와 트레일러는 `IN` 조회로 묶어서 쿼리 수가 데이터 건수에 따라 불필요하게 늘어나지 않게 했습니다. 또 `movie_genre`에는 `movie_id`, `genre_id`, `normalized_text` 인덱스를 정의해 영화별 장르 조회와 정규화 텍스트 기반 처리에도 대비했습니다.”

## 1. 현재 프로젝트에서 근거 있게 말할 수 있는 것

### 1-1. `movie_genre` 인덱스 설계

`movie_genre` 테이블에는 아래 인덱스가 정의되어 있다.

- `movie_id`
- `genre_id`
- `normalized_text`

의미:

- 영화별 장르 조회 최적화
- 장르 매핑 확장 대비
- 정규화 텍스트 기반 검색/중복 처리 대비

관련 코드:

- `src/main/java/com/onfilm/domain/movie/entity/MovieGenre.java`

## 1-2. 조회 패턴 최적화가 더 중요했던 부분

필모그래피 조회는 단순히 인덱스보다 조회 구조를 바꾼 쪽이 핵심이다.

현재 구조:

- `MoviePerson -> Movie`는 `join fetch`
- 장르는 `movieIds` 기준 `IN` 조회
- 트레일러도 `movieIds` 기준 `IN` 조회
- 이후 메모리에서 DTO 조립

이 구조의 장점:

- 연관 엔티티를 순회하며 추가 조회하는 `N+1` 가능성을 줄임
- 영화 수가 늘어나도 쿼리 수가 예측 가능함

관련 코드:

- `src/main/java/com/onfilm/domain/movie/service/MovieReadService.java`
- `src/main/java/com/onfilm/domain/movie/repository/MoviePersonRepository.java`
- `src/main/java/com/onfilm/domain/movie/repository/MovieGenreRepository.java`
- `src/main/java/com/onfilm/domain/movie/repository/TrailerRepository.java`

## 2. 면접 답변 문안

“이 프로젝트에서는 먼저 인덱스만 추가하기보다 조회 패턴 자체를 줄이는 데 집중했습니다. 대표적으로 필모그래피 조회에서 `MoviePerson`과 `Movie`는 `join fetch`로 가져오고, 장르와 트레일러는 각각 `IN` 조회 한 번씩만 수행하도록 구조를 바꿨습니다. 그래서 데이터 건수에 따라 쿼리 수가 불필요하게 늘어나는 문제를 줄일 수 있었습니다. 또 `movie_genre`에는 `movie_id`, `genre_id`, `normalized_text` 인덱스를 정의해서 영화별 장르 조회와 정규화 텍스트 기반 처리에도 대비했습니다.”

## 3. 수치 질문이 들어오면

현재 저장소에는 측정 로그가 없으므로, 임의의 수치를 말하면 안 된다.

안전한 답변:

“정확한 개선 수치는 별도 벤치마크가 필요합니다. 다만 현재 구조는 사람 조회 1회, `MoviePerson+Movie` 조회 1회, 장르 `IN` 조회 1회, 트레일러 `IN` 조회 1회 수준으로 쿼리 수가 고정되기 때문에, 데이터가 늘어날 때 반복 조회 구조보다 훨씬 안정적입니다.”

## 4. 실제 수치를 만들려면

면접 전에 아래처럼 직접 측정하면 된다.

- 테스트 데이터: 배우 1명, 영화 100건, 장르 300건, 트레일러 100건
- 변경 전: 총 SQL 수, 평균 응답 시간, `p95`
- 변경 후: 총 SQL 수, 평균 응답 시간, `p95`
- 측정 방식: Hibernate SQL 로그, MySQL `EXPLAIN`, `k6`

## 5. 추가로 개선할 만한 부분

현재는 `MoviePerson`의 조회 쿼리에 정렬과 필터 조건이 있다.

향후 고려할 만한 인덱스:

- `movie_person(person_id, sort_order, id)`
- `trailer(movie_id)`

단, 이건 실제 `EXPLAIN` 결과와 트래픽을 보고 판단해야 한다.
