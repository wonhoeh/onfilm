package com.onfilm.domain.genre.dto;

import com.onfilm.domain.genre.entity.Genre;

public record GenreAutocompleteResponse(
        Long id,
        String name
) {
    public static GenreAutocompleteResponse from(Genre genre) {
        return new GenreAutocompleteResponse(
                genre.getId(),
                genre.getName()
        );
    }
}
