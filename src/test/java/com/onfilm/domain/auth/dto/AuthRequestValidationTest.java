package com.onfilm.domain.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMalformedEmailAndUsername() {
        SignupRequest request = new SignupRequest("not-an-email", "password123!", "한글이름");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "username");
    }

    @Test
    void rejectsPasswordOverBcryptUtf8ByteLimit() {
        SignupRequest request = new SignupRequest(
                "user@example.com",
                "가".repeat(25),
                "valid_user"
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("password");
    }
}
