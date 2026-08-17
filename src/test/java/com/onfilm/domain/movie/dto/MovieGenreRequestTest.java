package com.onfilm.domain.movie.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovieGenreRequestTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsExactlyOneGenreSource() {
        MovieGenreRequest standard = new MovieGenreRequest(1L, null);
        MovieGenreRequest custom = new MovieGenreRequest(null, "Neo Noir");

        assertThat(validator.validate(standard)).isEmpty();
        assertThat(validator.validate(custom)).isEmpty();
    }

    @Test
    void rejectsMissingOrDuplicatedGenreSource() {
        MovieGenreRequest missing = new MovieGenreRequest(null, "   ");
        MovieGenreRequest duplicated = new MovieGenreRequest(1L, "Neo Noir");

        assertThat(validator.validate(missing)).isNotEmpty();
        assertThat(validator.validate(duplicated)).isNotEmpty();
    }
}
