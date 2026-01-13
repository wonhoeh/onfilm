package com.onfilm.domain.movie.dto;

import java.util.List;

public record StoryboardSceneResponse(
        Long sceneId,
        String title,
        String scriptHtml,
        int sortOrder,
        List<StoryboardCardResponse> cards
) {
}
