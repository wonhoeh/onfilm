package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.PersonRole;
import lombok.Getter;

@Getter
public class FilmographyResponse {
    private final Long movieId;
    private final String movieTitle;
    private final PersonRole role;
    private final String characterName;

    public FilmographyResponse(MoviePerson moviePerson) {
        this.movieId = moviePerson.getMovie().getId();
        this.movieTitle = moviePerson.getMovie().getTitle();
        this.role = moviePerson.getRole();
        this.characterName = moviePerson.getCharacterName();
    }
}
