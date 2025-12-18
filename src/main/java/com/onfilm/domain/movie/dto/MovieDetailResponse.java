package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

@Getter
public class MovieDetailResponse {
    private final Long id;
    private final String title;
    private final String synopsis;

    public MovieDetailResponse(Movie movie) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.synopsis = movie.getSynopsis();
    }
}
