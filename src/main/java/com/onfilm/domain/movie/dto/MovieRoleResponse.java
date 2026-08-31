package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.MoviePersonRole;
import com.onfilm.domain.movie.entity.PersonRole;

public record MovieRoleResponse(
        PersonRole role,
        CastType castType,
        String characterName
) {
    public static MovieRoleResponse from(MoviePersonRole moviePersonRole) {
        return new MovieRoleResponse(
                moviePersonRole.getRole(),
                moviePersonRole.getCastType(),
                moviePersonRole.getCharacterName()
        );
    }
}
