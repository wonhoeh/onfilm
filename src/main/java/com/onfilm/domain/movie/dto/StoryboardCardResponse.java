package com.onfilm.domain.movie.dto;

public record StoryboardCardResponse(
        Long cardId,
        String imageKey,
        String imageUrl,
        int sortOrder
) {
}
