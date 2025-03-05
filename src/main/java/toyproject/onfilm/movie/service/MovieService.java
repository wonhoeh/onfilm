package toyproject.onfilm.movie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.entity.Actor;
import toyproject.onfilm.actor.repository.ActorRepository;
import toyproject.onfilm.director.entity.Director;
import toyproject.onfilm.director.repository.DirectorRepository;
import toyproject.onfilm.exception.*;
import toyproject.onfilm.genre.dto.GenreResponse;
import toyproject.onfilm.genre.entity.Genre;
import toyproject.onfilm.genre.repository.GenreRepository;
import toyproject.onfilm.like.repository.MovieLikeRepository;
import toyproject.onfilm.movie.dto.*;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;
import toyproject.onfilm.movieactor.dto.MovieActorNameResponse;
import toyproject.onfilm.movieactor.dto.MovieActorResponse;
import toyproject.onfilm.movieactor.dto.UpdateMovieActorRequest;
import toyproject.onfilm.movieactor.entity.MovieActor;
import toyproject.onfilm.movieactor.repository.MovieActorRepository;
import toyproject.onfilm.moviedirector.dto.MovieDirectorNameResponse;
import toyproject.onfilm.moviedirector.entity.MovieDirector;
import toyproject.onfilm.moviedirector.repository.MovieDirectorRepository;
import toyproject.onfilm.movietrailer.dto.MovieTrailerResponse;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.movietrailer.repository.MovieTrailerRepository;
import toyproject.onfilm.moviewriter.entity.MovieWriter;
import toyproject.onfilm.moviewriter.repository.MovieWriterRepository;
import toyproject.onfilm.writer.entity.Writer;
import toyproject.onfilm.writer.repository.WriterRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;
    private final DirectorRepository directorRepository;
    private final WriterRepository writerRepository;
    private final GenreRepository genreRepository;
    private final MovieLikeRepository movieLikeRepository;
    private final DummyS3Service dummyS3Service;
    private final MovieActorRepository movieActorRepository;
    private final MovieDirectorRepository movieDirectorRepository;
    private final MovieWriterRepository movieWriterRepository;
    private final MovieTrailerRepository movieTrailerRepository;


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
     * 영화 조회: id, title, runtime, ageRating, movieActors, movieTrailers
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
     * 홈화면: 영화 카드 조회
     */
    public MovieCardResponse findMovieCardById(Long id) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));

        //섬네일, 예고편 URL
        List<MovieTrailerResponse> movieTrailerResponse = movie.getMovieTrailers().stream()
                .map(movieTrailer -> new MovieTrailerResponse(movieTrailer))
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
                .map(movieTrailer -> new MovieTrailerResponse(movieTrailer))
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
                .map(actor -> new MovieActorNameResponse(actor))
                .distinct()
                .collect(Collectors.toList());


        //감독 이름
        List<MovieDirectorNameResponse> directorResponse = movie.getMovieDirectors().stream()
                .map(director -> new MovieDirectorNameResponse(director))
                .collect(Collectors.toList());

        return new MovieExtraInfoResponse(synopsis, actorResponse, directorResponse);
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
        //Movie 먼저 저장
        Movie movie = Movie.createMovie(
                request.getTitle(),
                request.getRuntime(),
                request.getAgeRating(),
                request.getReleaseDate(),
                request.getSynopsis(),
                null //movieUrl은 처음에는 저장안함, 나중에 트레일러, 썸네일, 파일 한꺼번에 저장
        );

        //먼저 저장해서 ID 부여
        //object references an unsaved transient instance - save the transient instance before flushing
        movieRepository.save(movie);

        //배우 ID로 조회 후 MovieActor 생성
        List<MovieActor> movieActors = request.getMovieActors().stream()
                .map(movieActorRequest -> {
                    Actor actor = actorRepository.findById(movieActorRequest.getActorId())
                            .orElseThrow(() -> new ActorNotFoundException("배우를 찾을 수 없습니다: " + movieActorRequest.getActorId()));
                    return MovieActor.createCasting(movie, actor, movieActorRequest.getActorRole());
                })
                .collect(Collectors.toList());



        //감독 ID로 조회 후 MovieDirector 생성
        List<MovieDirector> movieDirectors = request.getDirectorIds().stream()
                .map(id -> {
                    Director director = directorRepository.findById(id)
                            .orElseThrow(() -> new DirectorNotFoundException("감독을 찾을 수 없습니다: " + id));
                    return MovieDirector.createMovieDirector(movie, director);
                })
                .collect(Collectors.toList());




        //작가 ID로 조회 후 MovieDirector 생성
        List<MovieWriter> movieWriters = request.getWriterIds().stream()
                .map(id -> {
                    Writer writer = writerRepository.findById(id)
                            .orElseThrow(() -> new WriterNotFoundException("작가를 찾을 수 없습니다: " + id));
                    return MovieWriter.createMovieWriter(movie, writer);
                })
                .collect(Collectors.toList());


        //장르 Id
        //CreateMovieRequest에서는 List<String>으로 받는다.
        //✅ Service 계층에서 ObjectId로 변환해서 조회한다.
        //✅ 조회한 Genre의 ObjectId를 toHexString()으로 변환해 Movie에 저장한다.


        //장르 변환 및 조회
        List<ObjectId> objectIdList = request.getGenreIds().stream()
                .map(ObjectId::new)
                .collect(Collectors.toList());

        List<Genre> genres = genreRepository.findAllById(
                objectIdList.stream()
                        .map(ObjectId::toString)
                        .collect(Collectors.toList()));

//        for(Genre genre : genres) {
//            log.info("genreName = {}", genre.getName());
//        }

        //=== 연관 관계 설정 ===//
//        for(MovieActor movieActor : movieActors) {
//            movie.addActor(movieActor);
//        }
//        for(MovieDirector movieDirector : movieDirectors) {
//            movie.addDirector(movieDirector);
//        }
//        for(MovieWriter movieWriter: movieWriters) {
//            movie.addWriter(movieWriter);
//        }
//        for(Genre genre : genres) {
//            movie.addGenre(genre.getId());
//        }
        movieActors.forEach(movie::addActor);
        movieDirectors.forEach(movie::addDirector);
        movieWriters.forEach(movie::addWriter);
        genres.forEach(genre -> movie.addGenre(genre.getId()));

        return movie.getId();
    }

    public void uploadMovieFiles(Long movieId, UploadMovieFileRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다: " + movieId));

        MovieTrailer movieTrailer = MovieTrailer.builder()
                .thumbnailUrl(request.getThumbnailUrl())
                .trailerUrl(request.getTrailerUrl())
                .build();

        movie.addTrailer(movieTrailer);
        movie.addMovieUrl(request.getMovieUrl());
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