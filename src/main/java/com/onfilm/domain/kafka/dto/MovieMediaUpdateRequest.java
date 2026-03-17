package com.onfilm.domain.kafka.dto;

public record MovieMediaUpdateRequest(
        String videoUrl,
        String thumbnailUrl
) {
}
