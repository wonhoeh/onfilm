package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

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
public class MovieResponse {
    private Long id;
    private String title;
    private int runtime;
    private String ageRating;
    private List<MovieActorResponse> movieActors;
    private List<MovieTrailerResponse> movieTrailers;

    public MovieResponse(Movie movie, List<MovieActorResponse> movieActors, List<MovieTrailerResponse> movieTrailers) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.runtime = movie.getRuntime();
        this.ageRating = movie.getAgeRating();
        this.movieActors = movieActors;
        this.movieTrailers = movieTrailers;
    }
}
