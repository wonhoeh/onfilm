package com.onfilm.domain.movie.dto;

public record GalleryItemPrivacyRequest(
        String key,
        boolean isPrivate
) {}
