package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FilmographyItemPrivacyRequest(
        @NotNull(message = "영화 ID는 필수입니다.")
        @Positive(message = "영화 ID는 양수여야 합니다.") Long movieId,
        boolean isPrivate
) {
}
