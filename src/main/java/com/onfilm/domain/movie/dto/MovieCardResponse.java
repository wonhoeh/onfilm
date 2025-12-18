package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

@Getter
public class MovieCardResponse {
    private final Long id;
    private final String title;
    private final Integer releaseYear;

    public MovieCardResponse(Movie movie) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.releaseYear = movie.getReleaseYear();
    }
}
