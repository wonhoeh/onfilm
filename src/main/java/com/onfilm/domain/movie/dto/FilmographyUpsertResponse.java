package com.onfilm.domain.movie.dto;

import java.util.List;

public record FilmographyUpsertResponse(
        List<Item> items
) {
    public record Item(
            String clientKey,
            Long movieId
    ) {}
}
