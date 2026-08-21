package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.PersonRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateMovieRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidRequest() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void rejectsInvalidRequiredFieldsAndRanges() {
        CreateMovieRequest request = new CreateMovieRequest(
                " ", 0, 1899, " ", null, null, null, null, null, null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("title", "runtime", "releaseYear", "movieUrl", "genres", "ageRating", "role");
    }

    @Test
    void rejectsTooLongStorageKeyAndInvalidNestedGenre() {
        CreateMovieRequest request = new CreateMovieRequest(
                "영화", 120, 2020, "a".repeat(Movie.STORAGE_KEY_MAX_LENGTH + 1), null,
                List.of(new MovieGenreRequest(null, null)), AgeRating.ALL, PersonRole.DIRECTOR, null, null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("movieUrl", "genres[0].sourceValid");
    }

    private static CreateMovieRequest validRequest() {
        return new CreateMovieRequest(
                "영화", 120, 2020, "movies/1/original.mp4", null,
                List.of(), AgeRating.ALL, PersonRole.DIRECTOR, null, null
        );
    }
}
