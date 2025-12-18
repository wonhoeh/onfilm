package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import lombok.Getter;

@Getter
public class MovieThumbnailResponse {
    private final Long id;
    private final String title;
    private final String thumbnailUrl;

    public MovieThumbnailResponse(Movie movie) {
        this.id = movie.getId();
        this.title = movie.getTitle();
        this.thumbnailUrl = movie.getThumbnailUrl();
    }
}
