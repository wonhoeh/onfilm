package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.PersonRole;

import java.util.List;

public record FilmographyUpsertRequest(
        List<Item> items
) {
    public record Item(
            String clientKey,
            Long movieId,
            String title,
            int runtime,
            Integer releaseYear,
            AgeRating ageRating,
            List<String> rawGenreTexts,
            PersonRole role,
            CastType castType,
            String characterName
    ) {}
}
