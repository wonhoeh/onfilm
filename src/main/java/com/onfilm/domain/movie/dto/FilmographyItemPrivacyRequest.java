package com.onfilm.domain.movie.dto;

public record FilmographyItemPrivacyRequest(
        Long movieId,
        boolean isPrivate
) {}
