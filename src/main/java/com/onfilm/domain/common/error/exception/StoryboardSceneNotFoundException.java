package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class StoryboardSceneNotFoundException extends DomainException {
    public StoryboardSceneNotFoundException(Long sceneId) {
        super(ErrorCode.STORYBOARD_SCENE_NOT_FOUND);
    }
}
