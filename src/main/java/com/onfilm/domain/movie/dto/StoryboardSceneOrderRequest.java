package com.onfilm.domain.movie.dto;

import java.util.List;

public record StoryboardSceneOrderRequest(
        List<Long> sceneIds
) {
}
