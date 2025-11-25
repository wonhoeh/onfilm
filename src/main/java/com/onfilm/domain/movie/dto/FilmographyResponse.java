package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.PersonRole;
import lombok.Builder;
import lombok.Getter;

@Getter
public class FilmographyResponse {

    private Long movieId;
    private String title;
    private PersonRole roleType;

    @Builder
    public FilmographyResponse(MoviePerson moviePerson) {
        this.movieId = moviePerson.getMovie().getId();
        this.title = moviePerson.getMovie().getTitle();
        this.roleType = moviePerson.getRole();
    }
}
