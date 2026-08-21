package com.onfilm.domain.movie.dto;

public record PublicIdByUsernameResponse(
        String username,
        String publicId
) {
}
