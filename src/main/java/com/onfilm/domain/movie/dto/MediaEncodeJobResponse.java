package com.onfilm.domain.movie.dto;

public record MediaEncodeJobResponse(
        String jobId,
        String sourceKey,
        String targetKey
) {
}
