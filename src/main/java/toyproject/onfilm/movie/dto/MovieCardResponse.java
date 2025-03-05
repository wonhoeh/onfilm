package toyproject.onfilm.movie.dto;

import lombok.Getter;
import toyproject.onfilm.genre.dto.GenreResponse;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movietrailer.dto.MovieTrailerResponse;

import java.util.List;

@Getter
public class MovieCardResponse {
    private Long id;
    private String title;
    private List<GenreResponse> genres;
    private int runtime;
    private List<MovieTrailerResponse> trailers;

    public MovieCardResponse(Movie movie, List<GenreResponse> genres, List<MovieTrailerResponse> trailers) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.runtime = movie.getRuntime();
        this.genres = genres;
        this.trailers = trailers;
    }
}
