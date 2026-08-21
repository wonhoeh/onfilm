package com.onfilm.domain.movie.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonDtoValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsFutureBirthDateAndMissingReplacementLists() {
        CreatePersonRequest request = new CreatePersonRequest(
                "이름", LocalDate.now().plusDays(1), null, null, null, null, null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("birthDate", "snsList", "rawTags");
    }

    @Test
    void validatesNestedSnsAndTagElements() {
        CreatePersonRequest request = new CreatePersonRequest(
                "이름", null, null, null, null,
                List.of(new CreatePersonSnsRequest(null, " ")),
                List.of(" ")
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("snsList[0].type", "snsList[0].url", "rawTags[0].<list element>");
    }

    @Test
    void acceptsLegacyProfileImageUrlAsProfileImageKey() throws Exception {
        String json = """
                {"name":"이름","profileImageUrl":"profiles/1/avatar.png","snsList":[],"rawTags":[]}
                """;

        CreatePersonRequest request = new ObjectMapper().readValue(json, CreatePersonRequest.class);

        assertThat(request.profileImageKey()).isEqualTo("profiles/1/avatar.png");
    }
}
