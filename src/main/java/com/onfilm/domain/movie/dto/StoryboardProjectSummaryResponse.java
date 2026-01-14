package com.onfilm.domain.movie.dto;

public record StoryboardProjectSummaryResponse(
        Long projectId,
        String title,
        String previewScript,
        int sceneCount
) {
}
