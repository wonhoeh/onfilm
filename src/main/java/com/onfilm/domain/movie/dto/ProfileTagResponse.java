package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.ProfileTag;

public record ProfileTagResponse(
        String rawTag
) {
    public static ProfileTagResponse from(ProfileTag tag) {
        return new ProfileTagResponse(tag.getRawText());
    }
}
