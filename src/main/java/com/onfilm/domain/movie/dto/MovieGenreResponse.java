package com.onfilm.domain.movie.dto;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.movie.entity.MovieGenre;

public record MovieGenreResponse(
        Long movieGenreId,
        Long genreId,
        String name,
        boolean custom
) {
    public static MovieGenreResponse from(MovieGenre movieGenre) {
        Genre standardGenre = movieGenre.getGenre();

        return new MovieGenreResponse(
                movieGenre.getId(),
                standardGenre == null ? null : standardGenre.getId(),
                movieGenre.getRawText(),
                standardGenre == null
        );
    }
}
