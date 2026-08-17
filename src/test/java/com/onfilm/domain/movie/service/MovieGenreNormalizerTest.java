package com.onfilm.domain.movie.service;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.dto.MovieGenreRequest;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MovieGenreNormalizerTest {

    @Mock
    private GenreRepository genreRepository;

    @InjectMocks
    private MovieGenreNormalizer movieGenreNormalizer;

    @Test
    void attachGenre_usesSelectedActiveStandardGenre() {
        Movie movie = createMovie();
        Genre standardGenre = standardGenre(1L, "Action", "action");
        given(genreRepository.findActiveByIds(List.of(1L)))
                .willReturn(List.of(standardGenre));

        movieGenreNormalizer.attachGenre(
                movie,
                List.of(new MovieGenreRequest(1L, null))
        );

        assertThat(movie.getGenres()).singleElement().satisfies(movieGenre -> {
            assertThat(movieGenre.getGenre()).isSameAs(standardGenre);
            assertThat(movieGenre.getRawText()).isEqualTo("Action");
            assertThat(movieGenre.getNormalizedText()).isEqualTo("action");
        });
    }

    @Test
    void attachGenre_storesUnmatchedTextAsCustomGenre() {
        Movie movie = createMovie();
        given(genreRepository.findActiveByNormalizedValues(List.of("neo noir")))
                .willReturn(List.of());

        movieGenreNormalizer.attachGenre(
                movie,
                List.of(new MovieGenreRequest(null, "  Neo Noir  "))
        );

        assertThat(movie.getGenres()).singleElement().satisfies(movieGenre -> {
            assertThat(movieGenre.getGenre()).isNull();
            assertThat(movieGenre.getRawText()).isEqualTo("Neo Noir");
            assertThat(movieGenre.getNormalizedText()).isEqualTo("neo noir");
        });
        verify(genreRepository)
                .findActiveByNormalizedValues(List.of("neo noir"));
        verify(genreRepository, never()).save(any(Genre.class));
    }

    @Test
    void attachGenre_promotesMatchingCustomTextToStandardGenre() {
        Movie movie = createMovie();
        Genre standardGenre = standardGenre(null, "Action", "action");
        given(genreRepository.findActiveByNormalizedValues(List.of("action")))
                .willReturn(List.of(standardGenre));

        movieGenreNormalizer.attachGenre(
                movie,
                List.of(new MovieGenreRequest(null, "  #ACTION  "))
        );

        assertThat(movie.getGenres()).singleElement().satisfies(movieGenre -> {
            assertThat(movieGenre.getGenre()).isSameAs(standardGenre);
            assertThat(movieGenre.getRawText()).isEqualTo("Action");
            assertThat(movieGenre.getNormalizedText()).isEqualTo("action");
        });
    }

    @Test
    void attachGenre_deduplicatesSelectedAndCustomStandardGenre() {
        Movie movie = createMovie();
        Genre standardGenre = standardGenre(1L, "Action", "action");
        given(genreRepository.findActiveByIds(List.of(1L)))
                .willReturn(List.of(standardGenre));
        given(genreRepository.findActiveByNormalizedValues(List.of("action")))
                .willReturn(List.of(standardGenre));

        movieGenreNormalizer.attachGenre(
                movie,
                List.of(
                        new MovieGenreRequest(null, "action"),
                        new MovieGenreRequest(1L, null)
                )
        );

        assertThat(movie.getGenres()).singleElement().satisfies(movieGenre ->
                assertThat(movieGenre.getGenre()).isSameAs(standardGenre)
        );
    }

    @Test
    void attachGenre_rejectsInactiveOrMissingStandardGenre() {
        Movie movie = createMovie();
        given(genreRepository.findActiveByIds(List.of(99L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> movieGenreNormalizer.attachGenre(
                movie,
                List.of(new MovieGenreRequest(99L, null))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("active genre not found: 99");
    }

    private static Genre standardGenre(Long id, String name, String normalized) {
        Genre genre = mock(Genre.class);
        if (id != null) {
            given(genre.getId()).willReturn(id);
        }
        given(genre.getName()).willReturn(name);
        given(genre.getNormalized()).willReturn(normalized);
        return genre;
    }

    private static Movie createMovie() {
        return Movie.create(
                "Test Movie",
                120,
                2020,
                "movie-key",
                null,
                List.of(),
                AgeRating.ALL
        );
    }
}
