package com.onfilm.domain.movie.dto;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.movie.entity.MovieGenre;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MovieGenreResponseTest {

    @Test
    void from_distinguishesStandardGenre() {
        Genre genre = mock(Genre.class);
        MovieGenre movieGenre = mock(MovieGenre.class);
        given(genre.getId()).willReturn(1L);
        given(movieGenre.getId()).willReturn(10L);
        given(movieGenre.getGenre()).willReturn(genre);
        given(movieGenre.getRawText()).willReturn("Action");

        MovieGenreResponse response = MovieGenreResponse.from(movieGenre);

        assertThat(response).isEqualTo(
                new MovieGenreResponse(10L, 1L, "Action", false)
        );
    }

    @Test
    void from_distinguishesCustomGenre() {
        MovieGenre movieGenre = mock(MovieGenre.class);
        given(movieGenre.getId()).willReturn(11L);
        given(movieGenre.getRawText()).willReturn("화려한 액션");

        MovieGenreResponse response = MovieGenreResponse.from(movieGenre);

        assertThat(response).isEqualTo(
                new MovieGenreResponse(11L, null, "화려한 액션", true)
        );
    }
}
