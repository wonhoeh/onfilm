package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.MovieTrailer;
import lombok.Getter;

@Getter
public class MovieTrailerResponse {
    private String name;
    private String url;

    public MovieTrailerResponse(MovieTrailer movieTrailer) {
        this.name = movieTrailer.getName();
        this.url = movieTrailer.getUrl();
    }
}
