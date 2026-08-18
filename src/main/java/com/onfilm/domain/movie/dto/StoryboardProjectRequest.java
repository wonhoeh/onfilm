package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.onfilm.domain.movie.entity.StoryboardProject.TITLE_MAX_LENGTH;

public record StoryboardProjectRequest(
        @NotBlank(message = "스토리보드 제목은 필수입니다.")
        @Size(max = TITLE_MAX_LENGTH, message = "스토리보드 제목은 120자 이하여야 합니다.")
        String title
) {
}
