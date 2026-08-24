package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class StoryboardProjectNotFoundException extends DomainException {
    public StoryboardProjectNotFoundException(Long projectId) {
        super(ErrorCode.STORYBOARD_PROJECT_NOT_FOUND);
    }
}
