package toyproject.onfilm.movie.dto;

import lombok.Getter;
import toyproject.onfilm.genre.dto.GenreResponse;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movieactor.dto.MovieActorResponse;
import toyproject.onfilm.movietrailer.dto.MovieTrailerResponse;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MovieDetailResponse {
    private Long id;
    private String title;
    private LocalDateTime releaseDate;
    private String ageRating;
    private int runtime;
    private List<GenreResponse> genres;
    private List<MovieTrailerResponse> trailers;
    private Long movieLikeCount;

    public MovieDetailResponse(Movie movie, List<GenreResponse> genres, List<MovieTrailerResponse> trailers, Long movieLikeCount) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.releaseDate = movie.getReleaseDate();
        this.ageRating = movie.getAgeRating();
        this.runtime = movie.getRuntime();
        this.genres = genres;
        this.trailers = trailers;
        this.movieLikeCount = movieLikeCount;
    }
}
