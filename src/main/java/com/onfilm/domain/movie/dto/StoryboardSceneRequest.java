package com.onfilm.domain.movie.dto;

import java.util.List;

public record StoryboardSceneRequest(
        Long sceneId,
        String title,
        String scriptHtml,
        List<StoryboardCardRequest> cards
) {
}
