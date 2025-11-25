package com.onfilm.domain.movie.dto;

import com.onfilm.domain.genre.dto.GenreResponse;
import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class MovieDetailResponse {
    private Long id;
    private String title;
    private LocalDate releaseDate;
    private String ageRating;
    private int runtime;
    private List<GenreResponse> genres;
    private List<MovieTrailerResponse> trailers;
    private Long movieLikeCount;

    public MovieDetailResponse(Movie movie, List<GenreResponse> genres,
                               List<MovieTrailerResponse> trailers, Long movieLikeCount) {
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
