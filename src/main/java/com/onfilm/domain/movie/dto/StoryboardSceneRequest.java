package com.onfilm.domain.movie.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.onfilm.domain.movie.entity.StoryboardScene.TITLE_MAX_LENGTH;

public record StoryboardSceneRequest(
        Long sceneId,
        @Size(max = TITLE_MAX_LENGTH, message = "씬 제목은 120자 이하여야 합니다.")
        String title,
        String scriptHtml,
        @NotNull(message = "카드 목록은 필수입니다.")
        List<@NotNull(message = "카드는 null일 수 없습니다.") @Valid StoryboardCardRequest> cards
) {
}
