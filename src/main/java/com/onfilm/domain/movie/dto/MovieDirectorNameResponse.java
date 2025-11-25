package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.MovieDirector;
import lombok.Getter;

@Getter
public class MovieDirectorNameResponse {
    private String name;

    public MovieDirectorNameResponse(MovieDirector movieDirector) {
        this.name = movieDirector.getDirector().getName();
    }
}
