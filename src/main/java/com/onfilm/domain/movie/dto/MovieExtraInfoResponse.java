package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

@Getter
public class MovieExtraInfoResponse {
    private final Long id;
    private final Integer releaseYear;
    private final int runtime;

    public MovieExtraInfoResponse(Movie movie) {
        this.id = movie.getId();
        this.releaseYear = movie.getReleaseYear();
        this.runtime = movie.getRuntime();
    }
}
