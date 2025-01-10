package toyproject.onfilm.movie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.actor.repository.ActorRepository;
import toyproject.onfilm.exception.MovieNotFoundException;
import toyproject.onfilm.movie.dto.MovieDto;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;
import toyproject.onfilm.movieactor.dto.MovieActorDto;
import toyproject.onfilm.movietrailer.dto.MovieTrailerDto;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;

    /**
     * GET /movies/{movieId}
     *
     * Response:
     * {
     *   "id": 123,
     *   "title": "영화 제목",
     *   "runtime": "120분",
     *   "ageRating": "15세 이상",
     *   "actor": "이병헌", "류승룡"
     *   "trailerUrl": "https://example.com/trailer.mp4"
     *   "trailerThumbnailUrl": "http://example.com/trailer.jpg"
//     *   "rating": 4.5,
//     *   "genre": ["액션", "스릴러"],
     * }
     */
    public MovieDto getMovieById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다."));

        List<MovieTrailerDto> movieTrailerDto = movie.getMovieTrailers().stream()
                .map(movieTrailer -> new MovieTrailerDto(
                        movieTrailer.getTrailUrl(),
                        movieTrailer.getThumbnailUrl()
                ))
                .collect(Collectors.toList());

        List<MovieActorDto> movieActorDto = movie.getMovieActors().stream()
                .map(movieActor -> new MovieActorDto(
                        movieActor.getActor().getProfile().getName(),
                        movieActor.getActor().getProfile().getAge(),
                        movieActor.getActor().getProfile().getSns(),
                        movieActor.getActorsRole()))
                .collect(Collectors.toList());

        return MovieDto.builder()
                .id(movie.getId())
                .title(movie.getTitle())
                .runtime(movie.getRuntime())
                .ageRating(movie.getAgeRating())
                .movieTrailers(movieTrailerDto)
                .movieActors(movieActorDto)
                .build();
    }
}