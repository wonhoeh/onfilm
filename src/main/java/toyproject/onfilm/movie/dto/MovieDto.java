package toyproject.onfilm.movie.dto;

import lombok.Builder;
import lombok.Getter;
import toyproject.onfilm.movieactor.dto.MovieActorDto;
import toyproject.onfilm.movietrailer.dto.MovieTrailerDto;

import java.util.List;

/**
 * GET /movies/{movieId}
 *
 * Response:
 * {
 *   "id": 123,
 *   "title": "영화 제목",
 *   "runtime": "120분",
 *   "ageRating": "15세 이상",
 *   "trailerUrl": "https://example.com/trailer.mp4"
 *   "trailerThumbnailUrl": "http://example.com/trailer.jpg"
 //     *   "rating": 4.5,
 //     *   "genre": ["액션", "스릴러"],
 * }
 */
@Getter
public class MovieDto {
    private Long id;
    private String title;
    private int runtime;
    private String ageRating;
    private List<MovieActorDto> movieActors;
    private List<MovieTrailerDto> movieTrailers;

    @Builder
    public MovieDto(Long id, String title, int runtime, String ageRating, List<MovieActorDto> movieActors, List<MovieTrailerDto> movieTrailers) {
        this.id = id;
        this.title = title;
        this.runtime = runtime;
        this.ageRating = ageRating;
        this.movieActors = movieActors;
        this.movieTrailers = movieTrailers;
    }
}
