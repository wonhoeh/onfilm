package com.onfilm.domain.movie.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoviePersonTest {

    @Test
    void create_attachesParticipantAndMultipleRolesToMovie() {
        Movie movie = createMovie();
        Person person = createPerson();

        MoviePerson moviePerson = movie.addMoviePerson(
                person,
                List.of(
                        role(PersonRole.ACTOR, CastType.LEAD, "  코브  "),
                        role(PersonRole.DIRECTOR, null, null),
                        role(PersonRole.WRITER, null, null)
                )
        );

        assertThat(moviePerson.getMovie()).isSameAs(movie);
        assertThat(movie.getMoviePeople()).containsExactly(moviePerson);
        assertThat(moviePerson.getRoles())
                .extracting(MoviePersonRole::getRole)
                .containsExactly(PersonRole.ACTOR, PersonRole.DIRECTOR, PersonRole.WRITER);
        assertThat(moviePerson.getRoles().get(0).getCharacterName()).isEqualTo("코브");
        assertThat(moviePerson.getRoles())
                .allSatisfy(moviePersonRole ->
                        assertThat(moviePersonRole.getMoviePerson()).isSameAs(moviePerson));
    }

    @Test
    void create_actorRequiresCastType() {
        assertThatThrownBy(() -> createMovie().addMoviePerson(
                createPerson(),
                List.of(role(PersonRole.ACTOR, null, null))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("castType is required");
    }

    @Test
    void create_nonActorRejectsActorDetails() {
        assertThatThrownBy(() -> createMovie().addMoviePerson(
                createPerson(),
                List.of(role(PersonRole.DIRECTOR, CastType.LEAD, "사용되지 않는 배역"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("actor details are only allowed for actor role");
    }

    @Test
    void create_rejectsEmptyAndDuplicateRoles() {
        assertThatThrownBy(() -> createMovie().addMoviePerson(createPerson(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("at least one role is required");

        assertThatThrownBy(() -> createMovie().addMoviePerson(
                createPerson(),
                List.of(
                        role(PersonRole.DIRECTOR, null, null),
                        role(PersonRole.DIRECTOR, null, null)
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate movie person role");
    }

    @Test
    void replaceRoles_keepsExistingRoleAndDetachesRemovedRole() {
        MoviePerson moviePerson = createMovie().addMoviePerson(
                createPerson(),
                List.of(
                        role(PersonRole.ACTOR, CastType.SUPPORTING, "친구"),
                        role(PersonRole.DIRECTOR, null, null)
                )
        );
        MoviePersonRole actor = moviePerson.getRoles().get(0);
        MoviePersonRole director = moviePerson.getRoles().get(1);

        moviePerson.replaceRoles(List.of(
                role(PersonRole.ACTOR, CastType.LEAD, "주인공"),
                role(PersonRole.WRITER, null, null)
        ));

        assertThat(moviePerson.getRoles().get(0)).isSameAs(actor);
        assertThat(actor.getCastType()).isEqualTo(CastType.LEAD);
        assertThat(actor.getCharacterName()).isEqualTo("주인공");
        assertThat(director.getMoviePerson()).isNull();
        assertThat(moviePerson.getRoles())
                .extracting(MoviePersonRole::getRole)
                .containsExactly(PersonRole.ACTOR, PersonRole.WRITER);
    }

    @Test
    void changeSortOrder_rejectsNegativeValue() {
        MoviePerson moviePerson = createMovie().addMoviePerson(
                createPerson(),
                PersonRole.ACTOR,
                CastType.SUPPORTING,
                null
        );

        assertThatThrownBy(() -> moviePerson.changeSortOrder(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sortOrder must be zero or greater");
    }

    @Test
    void create_rejectsDuplicateParticipantInSameMovie() {
        Movie movie = createMovie();
        Person person = createPerson();
        movie.addMoviePerson(person, PersonRole.ACTOR, CastType.CAMEO, "행인");

        assertThatThrownBy(() -> movie.addMoviePerson(
                person,
                PersonRole.DIRECTOR,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate movie participant");
    }

    @Test
    void remove_detachesBothSidesAndCollectionsAreReadOnly() {
        Movie movie = createMovie();
        MoviePerson moviePerson = movie.addMoviePerson(
                createPerson(),
                PersonRole.DIRECTOR,
                null,
                null
        );

        assertThatThrownBy(() -> movie.getMoviePeople().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> moviePerson.getRoles().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        movie.removeMoviePerson(moviePerson);

        assertThat(movie.getMoviePeople()).isEmpty();
        assertThat(moviePerson.getMovie()).isNull();
    }

    @Test
    void removeRole_rejectsRemovingLastRole() {
        MoviePerson moviePerson = createMovie().addMoviePerson(
                createPerson(),
                PersonRole.WRITER,
                null,
                null
        );

        assertThatThrownBy(() -> moviePerson.removeRole(moviePerson.getRoles().get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("moviePerson must have at least one role");
    }

    private static MoviePerson.RoleRegistration role(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        return new MoviePerson.RoleRegistration(role, castType, characterName);
    }

    private static Movie createMovie() {
        return Movie.create(
                "인셉션",
                148,
                2010,
                "movie-key",
                null,
                AgeRating.ALL
        );
    }

    private static Person createPerson() {
        return Person.create(
                "테스트 배우",
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
