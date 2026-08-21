package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.PersonRole;

import java.util.List;

public record MovieCardResponse(
        Long movieId,
        String title,
        List<MovieGenreResponse> genres,
        int runtime,
        Integer releaseYear,
        AgeRating ageRating,
        String movieUrl,
        String thumbnailUrl,
        String trailerUrl,

        PersonRole personRole,
        CastType castType,
        String characterName,
        boolean isPrivate) {
    public MovieCardResponse {
        genres = List.copyOf(genres);
    }
}
