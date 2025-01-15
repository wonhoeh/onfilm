package toyproject.onfilm.movie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.actor.repository.ActorRepository;
import toyproject.onfilm.common.Profile;
import toyproject.onfilm.exception.MovieNotFoundException;
import toyproject.onfilm.movie.dto.CreateMovieRequest;
import toyproject.onfilm.movie.dto.MovieThumbnailResponse;
import toyproject.onfilm.movie.dto.MovieResponse;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;
import toyproject.onfilm.movieactor.dto.MovieActorResponse;
import toyproject.onfilm.movieactor.entity.MovieActor;
import toyproject.onfilm.movietrailer.dto.MovieTrailerResponse;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;
    private final DummyS3Service dummyS3Service;

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
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));
        Movie movieWithTrailers = movieRepository.findMovieWithTrailers(id);
        Movie movieWithActors = movieRepository.findMovieWithActors(id);

        List<MovieTrailerResponse> movieTrailerResponse = movieWithTrailers.getMovieTrailers().stream()
                .map(movieTrailer -> new MovieTrailerResponse(movieTrailer))
                .collect(Collectors.toList());

        List<MovieActorResponse> movieActorResponse = movieWithActors.getMovieActors().stream()
                .map(movieActor -> new MovieActorResponse(movieActor))
                .collect(Collectors.toList());

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
     * 해결방법3: 배치로 데이터 가져오기 (10개씩 설정)
     */
    public MovieResponse findMovieByIdV3(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));

        List<MovieTrailerResponse> movieTrailerResponse = movie.getMovieTrailers().stream()
                .map(movieTrailer -> new MovieTrailerResponse(movieTrailer))
                .collect(Collectors.toList());

        List<MovieActorResponse> movieActorResponse = movie.getMovieActors().stream()
                .map(movieActor -> new MovieActorResponse(movieActor))
                .collect(Collectors.toList());

        return new MovieResponse(movie, movieActorResponse, movieTrailerResponse);
    }

    /**
     * 영화 섬네일 반환
     * 쿠팡 플레이 메인 화면처럼
     */
    public List<MovieThumbnailResponse> findAllWithThumbnailUrl() {
        return movieRepository.findAllWithTrailers().stream()
                .map(movie -> new MovieThumbnailResponse(movie))
                .collect(Collectors.toList());
    }

    /**
     * 영화 등록
     */
    @Transactional
    public Long createMovie(CreateMovieRequest request) {
        //1. 영화 파일 및 섬네일 업로드
        String movieFileUrl = dummyS3Service.uploadFile(request.getMovieFile());
        String thumbnailUrl = dummyS3Service.uploadFile(request.getThumbnailFile());
        String trailerUrl = dummyS3Service.uploadFile(request.getTrailerFile());

        //2. Movie 엔티티 생성
        Movie movie = Movie.createMovie(request.getTitle(), request.getRuntime(),
                request.getAgeRating(), request.getReleaseDate(), request.getSynopsis(),
                movieFileUrl);
        movie.addTrailer(new MovieTrailer(trailerUrl, thumbnailUrl));

        //3. 출연 배우 정보 설정
        List<MovieActor> movieActors = request.getActors().stream().map(actorRequest -> {
            //배우 정보 저장 (중복 체크)
            Actor actor = actorRepository.findByProfileName(actorRequest.getName())
                    .orElseGet(() -> actorRepository.save(new Actor(new Profile(actorRequest.getName(), actorRequest.getAge(), actorRequest.getSns()))));

            //MovieActor 엔티티 생성
            MovieActor movieActor = MovieActor.createCasting(movie, actor, actorRequest.getActorsRole());
            return movieActor;
        }).collect(Collectors.toList());

        //4. 감독 정보 설정


        //5. 작가 정보 설정

        //영화와 배우 관계 설정
        for(MovieActor movieActor : movieActors) {
            movie.addActor(movieActor);
        }

        //4. 데이터베이스에 저장
        movieRepository.save(movie);

        //5. 생성된 영화의 ID 반환
        return movie.getId();
    }

}