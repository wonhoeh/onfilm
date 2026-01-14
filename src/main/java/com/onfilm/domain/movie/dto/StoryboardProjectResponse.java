package com.onfilm.domain.movie.dto;

import java.util.List;

public record StoryboardProjectResponse(
        Long projectId,
        String title,
        List<StoryboardSceneResponse> scenes
) {
}
