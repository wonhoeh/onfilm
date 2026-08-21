package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import static com.onfilm.domain.movie.entity.StoryboardCard.IMAGE_KEY_MAX_LENGTH;

public record StoryboardCardRequest(
        @Positive(message = "카드 ID는 양수여야 합니다.") Long cardId,
        @Size(max = IMAGE_KEY_MAX_LENGTH, message = "이미지 키는 512자 이하여야 합니다.")
        String imageKey
) {
}
