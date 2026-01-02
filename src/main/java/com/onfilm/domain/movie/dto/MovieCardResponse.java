package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.*;

import java.util.stream.Collectors;

public record MovieCardResponse(
        String title,
        String genre,
        int runtime,
        AgeRating ageRating,
        String movieUrl,
        String thumbnailUrl,
        String trailerUrl,

        PersonRole personRole,
        CastType castType,
        String characterName) {

    public static MovieCardResponse from(MoviePerson mp) {
        Movie m = mp.getMovie();

        String genreText = (m.getGenres() == null || m.getGenres().isEmpty())
                ? ""
                : m.getGenres().stream()
                // MovieGenre의 실제 getter에 맞게 수정
                .map(MovieGenre::getRawText)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" / "));

        String trailer = (m.getTrailers() == null || m.getTrailers().isEmpty())
                ? null
                : m.getTrailers().stream()
                .map(Trailer::getUrl)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);

        return new MovieCardResponse(
                m.getTitle(),
                genreText,
                m.getRuntime(),
                m.getAgeRating(),
                m.getMovieUrl(),
                m.getThumbnailUrl(),
                trailer,

                mp.getRole(),
                mp.getCastType(),
                mp.getCharacterName()
        );
    }
}
