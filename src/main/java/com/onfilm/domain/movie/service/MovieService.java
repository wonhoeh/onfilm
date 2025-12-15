package com.onfilm.domain.movie.service;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.common.error.exception.MoviePersonNotFoundException;
import com.onfilm.domain.like.repository.MovieLikeRepository;
import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.*;
import com.onfilm.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

import static com.onfilm.domain.movie.entity.MovieTrailerType.*;
import static com.onfilm.infra.s3.FileType.*;


@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;
    private final GenreRepository genreRepository;
    private final MovieLikeRepository movieLikeRepository;
    private final MovieActorRepository movieActorRepository;
    private final MovieDirectorRepository movieDirectorRepository;
    private final MovieWriterRepository movieWriterRepository;
    private final MovieTrailerRepository movieTrailerRepository;

    private final S3Service s3service;

    // deprecated
    private final ActorRepository actorRepository;

    /**
     * 영화 등록
     */
    @Transactional
    public Long createMovie(CreateMovieRequest request,
                            MultipartFile thumbnailFile,
                            MultipartFile movieFile,
                            List<MultipartFile> trailerFiles,
                            Map<String, MultipartFile> personProfileFiles
    ) {
        // 1. 영화, 섬네일 업로드
        String thumbnailUrl = s3service.upload(thumbnailFile, THUMBNAIL);
        String movieUrl = s3service.upload(movieFile, MOVIE);

        // 2. 영화 생성
        Movie movie = Movie.create(request, thumbnailUrl, movieUrl);

        // tempId -> Person 매핑
        Map<String, Person> tempPersonMap = new HashMap<>();

        // 3. 신규 Person 생성
        if (request.getPeople() != null) {
            for (CreatePersonRequest createPersonRequest : request.getPeople()) {
                MultipartFile profileFile  = personProfileFiles.get(createPersonRequest.getTempId());
                String profileUrl = s3service.upload(profileFile, PROFILE_IMAGE);

                Person person = Person.create(createPersonRequest, profileUrl);
                personRepository.save(person);

                tempPersonMap.put(createPersonRequest.getTempId(), person);
            }
        }

        // 4. MoviePerson 생성 (기존 + 신규 모두 지원)
        if (request.getMoviePeople() != null) {
            for (MoviePersonRequest moviePersonRequest : request.getMoviePeople()) {
                Person person;
                if (moviePersonRequest.getPersonId() != null) {
                    person = personRepository.findById(moviePersonRequest.getPersonId())
                            .orElseThrow(() -> new MoviePersonNotFoundException(""));
                } else {
                    person = tempPersonMap.get(moviePersonRequest.getTempId());
                }

                MoviePerson.create(movie, person, moviePersonRequest);
            }
        }

        // 5. 트레일러 업로드
        if (trailerFiles != null) {
            for (MultipartFile trailerFile : trailerFiles) {
                if (trailerFile == null || trailerFile.isEmpty()) continue;

                String trailerUrl = s3service.upload(trailerFile, TRAILER);
                MovieTrailer.create(movie, MAIN, trailerUrl);
            }
        }

        // 6. 기존 장르 매핑
        if (request.getGenreIds() != null) {
            for (Long genreId : request.getGenreIds()) {
                Genre genre = genreRepository.findById(genreId)
                        .orElseThrow(() -> new RuntimeException("존재하지 않는 장르"));
                movie.addGenre(genre);
            }
        }

        // 7. 신규 장르 매핑
        if (request.getNewGenres() != null) {
            for (String name : request.getNewGenres()) {
                String cleanName = name.trim();

                Genre genre = genreRepository.findByName(cleanName)
                        .orElseGet(() ->
                                genreRepository.save(
                                        Genre.builder()
                                                .name(cleanName)
                                                .description(null)
                                                .build()
                                )
                        );
                movie.addGenre(genre);
            }
        }

        // 7. 저장
        movieRepository.save(movie);

        return movie.getId();
    }

    /**
     * 카르테시안 곱 문제 발생
     * 해결방법1: 나눠서 쿼리 조회 movie x trailer, movie x actors
     * 해결방법2: 직접 DTO로 필요한 필드 받기
     * 해결방법3: 배치로 데이터 가져오기
     * 해결방법4: @EntityGraph 사용 (JPA 2.1 도입)
     * 3가지 모두 사용해보고 API로 남기기
     */

    /**
     * 해결방법1: 나눠서 쿼리 조회 movie x trailer, movie x actors
     */
    @Transactional  //하나의 트랜잭션 내에서 조회를 처리하는 것이 중요
    public MovieResponse findMovieByIdV1(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다." + id));
        Movie movieWithTrailers = movieRepository.findMovieWithTrailers(id);
        Movie movieWithActors = movieRepository.findMovieWithActors(id);

        List<MovieTrailerResponse> movieTrailerResponse = movieWithTrailers.getMovieTrailers().stream()
                .map(MovieTrailerResponse::new)
                .toList();

        List<MovieActorResponse> movieActorResponse = movieWithActors.getMoviePeople().stream()
                .map(MovieActorResponse::new)
                .toList();

        return new MovieResponse(movie, movieActorResponse, movieTrailerResponse);
    }




    /**
     * 해결방법2: 직접 DTO로 필요한 필드 받기
     */
//    public MovieDetailResponse findMovieByIdV2(Long id) {
//        List<MovieDetailsDto> movieDetails = movieRepository.findMovieDetails(id);
//
//        if(movieDetails.isEmpty()) {
//            throw new MovieNotFoundException("영화를 찾을 수 없습니다.");
//        }
//
//        MovieDetailsDto firstRow = movieDetails.get(0);
//        Long movieId = firstRow.getId();
//        String movieTitle = firstRow.getTitle();
//        int runtime = firstRow.getRuntime();
//        String ageRating = firstRow.getAgeRating();
//
//
//        //Trailer와 Actor 데이터 분리
//        List<MovieTrailerDto> trailers = movieDetails.stream()
//                .filter(row -> row.getTrailerUrl() != null)
//                .map(row -> new MovieTrailerDto(row))
//                .distinct()
//                .collect(Collectors.toList());
//
//        List<MovieActorDto> actors = movieDetails.stream()
//                .filter(row -> row.getName() != null)
//                .map(row -> new MovieActorDto(row))
//                .distinct()
//                .collect(Collectors.toList());
//
//        return new MovieDetailResponse(movieId, movieTitle, runtime, ageRating, trailers, actors);
//    }

    /**
     * 영화 조회: id, title, runtime, ageRating, movieActors, movieTrailers
     * 해결방법3: 배치로 데이터 가져오기 (10개씩 설정)
     */
    public MovieResponse findMovieByIdV3(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));

        List<MovieTrailerResponse> movieTrailerResponse = movie.getMovieTrailers().stream()
                .map(MovieTrailerResponse::new)
                .collect(Collectors.toList());

        List<MovieActorResponse> movieActorResponse = movie.getMoviePeople().stream()
                .map(MovieActorResponse::new)
                .collect(Collectors.toList());

        return new MovieResponse(movie, movieActorResponse, movieTrailerResponse);
    }

    /**
     * 홈화면: 영화 카드 조회
     */
    public MovieCardResponse findMovieCardById(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));

        //섬네일, 예고편 URL
        List<MovieTrailerResponse> movieTrailerResponse = movie.getMovieTrailers().stream()
                .map(MovieTrailerResponse::new)
                .collect(Collectors.toList());

        //영화 장르
        List<GenreResponse> genreResponse = movie.getGenreIds().stream()
                .map(genreRepository::findById) // ID로 Genre 조회 (Optional<Genre> 반환)
                .filter(Optional::isPresent) // 존재하는 경우만 필터링
                .map(Optional::get) // Optional에서 값 추출
                .map(genre -> new GenreResponse(genre.getName())) // GenreCardResponse 변환
                .collect(Collectors.toList());

        return new MovieCardResponse(movie, genreResponse, movieTrailerResponse);
    }

    /**
     * 홈 화면에서 카드 또는 영화 리스트의 섬네일 클릭 -> 영화 상세 화면
     */
    public MovieDetailResponse findMovieDetailById(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다."));

        //섬네일, 예고편 URL
        List<MovieTrailerResponse> movieTrailerResponse = movie.getMovieTrailers().stream()
                .map(MovieTrailerResponse::new)
                .collect(Collectors.toList());

        //영화 장르
        List<GenreResponse> genreResponse = movie.getGenreIds().stream()
                .map(genreRepository::findById) // ID로 Genre 조회 (Optional<Genre> 반환)
                .filter(Optional::isPresent) // 존재하는 경우만 필터링
                .map(Optional::get) // Optional에서 값 추출
                .map(genre -> new GenreResponse(genre.getName())) // GenreCardResponse 변환
                .collect(Collectors.toList());

        long movieLikeCount = movieLikeRepository.countByMovieId(id);

        return new MovieDetailResponse(movie, genreResponse, movieTrailerResponse, movieLikeCount);
    }

    /**
     * 영화 추가 정보 조회
     */
    public MovieExtraInfoResponse findMovieExtraInfoById(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다."));
        //시놉시스
        String synopsis = movie.getSynopsis();

        //출연배우 이름
        List<MovieActorNameResponse> actorResponse = movie.getMovieActors().stream()
                .map(MovieActorNameResponse::new)
                .distinct()
                .collect(Collectors.toList());


        //감독 이름
        List<MovieDirectorNameResponse> directorResponse = movie.getMovieDirectors().stream()
                .map(MovieDirectorNameResponse::new)
                .collect(Collectors.toList());

        return new MovieExtraInfoResponse(synopsis, actorResponse, directorResponse);
    }

    /**
     * 영화 섬네일 반환
     * 쿠팡 플레이 메인 화면처럼
     */
    public List<MovieThumbnailResponse> findAllWithThumbnailUrl() {
        return movieRepository.findAllWithTrailers().stream()
                .map(MovieThumbnailResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * 페이징 적용
     * 최신 등록 기준
     */
    public Page<MovieThumbnailResponse> findAllByReleaseDate(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "releaseDate"));
        Page<Movie> movies = movieRepository.findAllByOrderByReleaseDateDesc(pageable);
        return movies.map(MovieThumbnailResponse::new);
    }

    /**
     * 영화 삭제
     */
    @Transactional
    public void deleteMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다: " + movieId));

        //연관 데이터 삭제
        movieActorRepository.deleteById(movie.getId());
        movieDirectorRepository.deleteById(movie.getId());
        movieWriterRepository.deleteById(movie.getId());
        movieTrailerRepository.deleteById(movie.getId());

        movieRepository.deleteById(movie.getId());
    }

    /**
     * 영화 수정
     */
    @Transactional
    public void updateMovie(Long movieId, UpdateMovieRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다: " + movieId));

        movie.updateMovie(request, actorRepository);
        movie.updateMovieActors(request.getMovieActors(), actorRepository);

    }

    /**
     * 영화 보기
     */
    public MovieWatchResponse watchMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다: " + movieId));

        return new MovieWatchResponse(movie.getTitle(), movie.getMovieUrl());
    }

    //영화와 장르 가져오기
//    @Transactional
//    public MovieGenreDto getMovieWithGenres(Long movieId) {
//        //RDB에서 Movie 데이터 조회
//        Movie movie = movieRepository.findById(movieId)
//                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));
//
//        //MongoDB에서 Genre 데이터 조회
//        List<Genre> genre = movie.getGenreIds().stream()
//                .map(genreId -> genreRepository.findById(genreId)
//                        .
//                .collect(Collectors.toList());
//
//        //Movie + Genre 정보를 MovieGenreDto로 변환
//        return new MovieGenreDto(movie, genre);
//    }
}