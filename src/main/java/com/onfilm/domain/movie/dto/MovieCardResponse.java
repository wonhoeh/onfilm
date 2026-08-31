package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import java.util.List;

public record MovieCardResponse(
        Long movieId,
        String title,
        List<MovieGenreResponse> genres,
        int runtime,
        Integer releaseYear,
        AgeRating ageRating,
        String movieUrl,
        String thumbnailUrl,
        String trailerUrl,

        List<MovieRoleResponse> roles,
        boolean isPrivate) {
    public MovieCardResponse {
        genres = List.copyOf(genres);
        roles = List.copyOf(roles);
    }
}
