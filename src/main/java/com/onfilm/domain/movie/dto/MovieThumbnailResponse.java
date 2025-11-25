package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieTrailer;
import lombok.Getter;

@Getter
public class MovieThumbnailResponse {
    private String title;
    private String thumbnailUrl;

    public MovieThumbnailResponse(Movie movie) {
        this.thumbnailUrl = movie.getThumbnailUrl();
        this.title = movie.getTitle();
    }
}
