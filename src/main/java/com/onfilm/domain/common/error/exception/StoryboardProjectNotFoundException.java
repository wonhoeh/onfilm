package com.onfilm.domain.common.error.exception;

public class StoryboardProjectNotFoundException extends RuntimeException {
    public StoryboardProjectNotFoundException(Long projectId) {
        super("STORYBOARD PROJECT NOT FOUND: " + projectId);
    }
}
