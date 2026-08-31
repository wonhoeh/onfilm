package com.onfilm.domain.movie.dto;

import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.PersonRole;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilmographyUpsertRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsMultipleDifferentRoles() {
        FilmographyUpsertRequest request = new FilmographyUpsertRequest(List.of(
                item(List.of(
                        new MovieRoleRequest(PersonRole.ACTOR, CastType.LEAD, "주인공"),
                        new MovieRoleRequest(PersonRole.DIRECTOR, null, null),
                        new MovieRoleRequest(PersonRole.WRITER, null, null)
                ))
        ));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsEmptyAndDuplicateRoles() {
        FilmographyUpsertRequest empty = new FilmographyUpsertRequest(List.of(item(List.of())));
        FilmographyUpsertRequest duplicate = new FilmographyUpsertRequest(List.of(item(List.of(
                new MovieRoleRequest(PersonRole.WRITER, null, null),
                new MovieRoleRequest(PersonRole.WRITER, null, null)
        ))));

        assertThat(validator.validate(empty))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("items[0].roles");
        assertThat(validator.validate(duplicate))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("items[0].rolesUnique");
    }

    private static FilmographyUpsertRequest.Item item(List<MovieRoleRequest> roles) {
        return new FilmographyUpsertRequest.Item(
                "client-1",
                1L,
                "영화",
                120,
                2020,
                AgeRating.ALL,
                List.of(),
                roles,
                false
        );
    }
}
