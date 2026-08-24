package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.FilmographyItemNotFoundException;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
}
