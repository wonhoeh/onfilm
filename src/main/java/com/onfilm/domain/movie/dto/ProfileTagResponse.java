package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.ProfileTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileTagResponse {
    private String rawTag;

    public static ProfileTagResponse from(ProfileTag tag) {
        return ProfileTagResponse.builder()
                .rawTag(tag.getRawText())
                .build();
    }
}
