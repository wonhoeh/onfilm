package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

@Getter
public class MovieResponse {
    private final Long id;
    private final String title;
    private final int runtime;
    private final AgeRating ageRating;

    public MovieResponse(Movie movie) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.runtime = movie.getRuntime();
        this.ageRating = movie.getAgeRating();
    }
}
