package com.onfilm.domain.movie.dto;

public record MediaUploadCompleteRequest(
        String sourceKey,
        String contentType
) {
}
