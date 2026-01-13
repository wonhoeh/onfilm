package com.onfilm.domain.common.error.exception;

public class StoryboardSceneNotFoundException extends RuntimeException {
    public StoryboardSceneNotFoundException(Long sceneId) {
        super("STORYBOARD_SCENE_NOT_FOUND: " + sceneId);
    }
}
