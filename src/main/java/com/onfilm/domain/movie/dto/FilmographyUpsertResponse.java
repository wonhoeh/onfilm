package com.onfilm.domain.movie.dto;

import java.util.List;

public record FilmographyUpsertResponse(
        List<Item> items
) {
    public FilmographyUpsertResponse {
        items = List.copyOf(items);
    }

    public record Item(
            String clientKey,
            Long movieId
    ) {
    }
}
