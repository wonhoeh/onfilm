package com.onfilm.domain.movie.dto;

import com.onfilm.domain.genre.dto.GenreResponse;
import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

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
