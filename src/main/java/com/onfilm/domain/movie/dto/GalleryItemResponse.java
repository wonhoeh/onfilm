package com.onfilm.domain.movie.dto;

public record GalleryItemResponse(
        String key,
        String url,
        boolean isPrivate
) {}
