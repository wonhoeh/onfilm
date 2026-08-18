package com.onfilm.domain.movie.repository;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class MovieGenrePersistenceTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieGenreRepository movieGenreRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void customGenre_isStoredOnlyInMovieGenreTable() {
        Movie movie = createMovie();
        movie.addCustomGenre("  화려한 액션  ");

        movieRepository.saveAndFlush(movie);
        entityManager.clear();

        assertThat(genreRepository.count()).isZero();
        assertThat(movieGenreRepository.findAll())
                .singleElement()
                .satisfies(movieGenre -> {
                    assertThat(movieGenre.getGenre()).isNull();
                    assertThat(movieGenre.getRawText()).isEqualTo("화려한 액션");
                    assertThat(movieGenre.getNormalizedText()).isEqualTo("화려한 액션");
                });
    }

    @Test
    void standardGenre_reusesExistingGenreWithoutCreatingAnotherOne() {
        Genre standardGenre = genreRepository.saveAndFlush(
                Genre.create("Action")
        );
        Movie movie = createMovie();
        movie.addStandardGenre(standardGenre);

        movieRepository.saveAndFlush(movie);
        entityManager.clear();

        assertThat(genreRepository.count()).isEqualTo(1);
        assertThat(movieGenreRepository.findAll())
                .singleElement()
                .satisfies(movieGenre -> {
                    assertThat(movieGenre.getGenre().getId())
                            .isEqualTo(standardGenre.getId());
                    assertThat(movieGenre.getRawText()).isEqualTo("Action");
                });
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
