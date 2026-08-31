package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.FilmographyItemNotFoundException;
import com.onfilm.domain.movie.dto.FilmographyUpsertRequest;
import com.onfilm.domain.movie.dto.MovieRoleRequest;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class FilmographyCommandServiceTest {

    @Test
    void privacyChangeThrowsNotFoundWhenFilmographyItemDoesNotExist() {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MoviePersonRepository moviePersonRepository = mock(MoviePersonRepository.class);
        MovieGenreNormalizer movieGenreNormalizer = mock(MovieGenreNormalizer.class);
        CurrentPersonProvider currentPersonProvider = mock(CurrentPersonProvider.class);
        FilmographyCommandService service = new FilmographyCommandService(
                movieRepository,
                moviePersonRepository,
                movieGenreNormalizer,
                currentPersonProvider
        );
        Person person = mock(Person.class);
        given(person.getId()).willReturn(1L);
        given(currentPersonProvider.getRequired("public-id")).willReturn(person);
        given(moviePersonRepository.findByPersonIdAndMovieId(1L, 2L)).willReturn(null);

        assertThatThrownBy(() -> service.changeItemPrivacy("public-id", 2L, true))
                .isInstanceOfSatisfying(FilmographyItemNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FILMOGRAPHY_ITEM_NOT_FOUND));
    }

    @Test
    void replace_updatesOneParticipationWithMultipleRoles() {
        MovieRepository movieRepository = mock(MovieRepository.class);
        MoviePersonRepository moviePersonRepository = mock(MoviePersonRepository.class);
        MovieGenreNormalizer movieGenreNormalizer = mock(MovieGenreNormalizer.class);
        CurrentPersonProvider currentPersonProvider = mock(CurrentPersonProvider.class);
        FilmographyCommandService service = new FilmographyCommandService(
                movieRepository,
                moviePersonRepository,
                movieGenreNormalizer,
                currentPersonProvider
        );
        Person person = mock(Person.class);
        Movie movie = Movie.create("기존 영화", 100, 2020, "movie-key", null, AgeRating.ALL);
        MoviePerson moviePerson = movie.addMoviePerson(
                person,
                PersonRole.DIRECTOR,
                null,
                null
        );
        setId(movie, 10L);
        given(person.getId()).willReturn(1L);
        given(currentPersonProvider.getRequired("public-id")).willReturn(person);
        given(moviePersonRepository.findFilmographyByPersonId(1L))
                .willReturn(List.of(moviePerson));
        FilmographyUpsertRequest request = new FilmographyUpsertRequest(List.of(
                new FilmographyUpsertRequest.Item(
                        "client-1",
                        10L,
                        "변경 영화",
                        120,
                        2021,
                        AgeRating.AGE_12,
                        List.of(),
                        List.of(
                                new MovieRoleRequest(PersonRole.ACTOR, CastType.LEAD, "주인공"),
                                new MovieRoleRequest(PersonRole.DIRECTOR, null, null),
                                new MovieRoleRequest(PersonRole.WRITER, null, null)
                        ),
                        false
                )
        ));

        service.replace("public-id", request);

        assertThat(moviePerson.getRoles())
                .extracting(MoviePersonRole::getRole)
                .containsExactly(PersonRole.ACTOR, PersonRole.DIRECTOR, PersonRole.WRITER);
        assertThat(moviePerson.getSortOrder()).isZero();
        verify(movieGenreNormalizer).attachGenre(movie, List.of());
    }

    private static void setId(Movie movie, Long id) {
        try {
            var field = Movie.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(movie, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
