package com.onfilm.domain.movie.dto;

import com.onfilm.domain.genre.entity.GenreName;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MovieGenreRequest(
        @Positive Long genreId,
        @Size(max = GenreName.MAX_LENGTH) String customText
) {
    @AssertTrue(message = "genreId or customText must be provided exclusively")
    public boolean isSourceValid() {
        return (genreId != null) ^ hasCustomText();
    }

    public boolean hasCustomText() {
        return customText != null && !customText.isBlank();
    }
}
