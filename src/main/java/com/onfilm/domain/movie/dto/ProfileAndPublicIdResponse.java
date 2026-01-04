package com.onfilm.domain.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileAndPublicIdResponse {
    private String username;
    private String publicId;
    private PersonResponse personResponse;
}
