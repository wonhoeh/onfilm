package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StoryboardSceneOrderRequest(
        @NotNull(message = "씬 순서는 필수입니다.")
        List<@NotNull(message = "씬 ID는 null일 수 없습니다.") Long> sceneIds
) {
}
