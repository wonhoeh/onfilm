package com.onfilm.domain.movie.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoviePersonTest {

    @Test
    void create_actor_attachesToMovieAndNormalizesCharacterName() {
        Movie movie = createMovie();
        Person person = createPerson();

        MoviePerson moviePerson = MoviePerson.create(
                movie,
                person,
                PersonRole.ACTOR,
                CastType.LEAD,
                "  코브  "
        );

        assertThat(moviePerson.getMovie()).isSameAs(movie);
        assertThat(movie.getMoviePeople()).containsExactly(moviePerson);
        assertThat(moviePerson.getCharacterName()).isEqualTo("코브");
    }

    @Test
    void create_nonActor_clearsCastTypeAndCharacterName() {
        MoviePerson moviePerson = MoviePerson.create(
                createMovie(),
                createPerson(),
                PersonRole.DIRECTOR,
                CastType.LEAD,
                "사용되지 않는 배역"
        );

        assertThat(moviePerson.getCastType()).isNull();
        assertThat(moviePerson.getCharacterName()).isNull();
    }

    @Test
    void create_actor_requiresCastType() {
        assertThatThrownBy(() -> MoviePerson.create(
                createMovie(),
                createPerson(),
                PersonRole.ACTOR,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("castType is required");
    }

    @Test
    void changeSortOrder_rejectsNegativeValue() {
        MoviePerson moviePerson = MoviePerson.create(
                createMovie(),
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
    void create_rejectsDuplicateCreditInSameMovie() {
        Movie movie = createMovie();
        Person person = createPerson();
        MoviePerson.create(movie, person, PersonRole.ACTOR, CastType.CAMEO, "행인");

        assertThatThrownBy(() -> MoviePerson.create(
                movie,
                person,
                PersonRole.ACTOR,
                CastType.CAMEO,
                "행인"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate movie credit");
    }

    private static Movie createMovie() {
        return Movie.create(
                "인셉션",
                148,
                2010,
                "movie-key",
                null,
                List.of(),
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
