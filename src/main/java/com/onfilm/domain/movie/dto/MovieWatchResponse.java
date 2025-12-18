package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

@Getter
public class MovieWatchResponse {
    private final Long id;
    private final String title;
    private final String movieUrl;

    public MovieWatchResponse(Movie movie) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.movieUrl = movie.getMovieUrl();
    }
}
