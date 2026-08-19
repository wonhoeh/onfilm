package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.Size;

import static com.onfilm.domain.movie.entity.StoryboardCard.IMAGE_KEY_MAX_LENGTH;

public record StoryboardCardRequest(
        Long cardId,
        @Size(max = IMAGE_KEY_MAX_LENGTH, message = "이미지 키는 512자 이하여야 합니다.")
        String imageKey
) {
}
