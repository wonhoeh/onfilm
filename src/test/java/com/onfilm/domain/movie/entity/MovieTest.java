package com.onfilm.domain.movie.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieTest {

    @Test
    void create_validatesAndNormalizesFields() {
        Movie movie = Movie.create(
                "  인셉션  ",
                148,
                2010,
                "  movie-key  ",
                "   ",
                List.of("  trailer-key  "),
                AgeRating.ALL
        );

        assertThat(movie.getTitle()).isEqualTo("인셉션");
        assertThat(movie.getRuntime()).isEqualTo(148);
        assertThat(movie.getReleaseYear()).isEqualTo(2010);
        assertThat(movie.getMovieUrl()).isEqualTo("movie-key");
        assertThat(movie.getThumbnailUrl()).isNull();
        assertThat(movie.getTrailers()).extracting(Trailer::getUrl)
                .containsExactly("trailer-key");
    }

    @Test
    void create_requiresReleaseYear() {
        assertThatThrownBy(() -> createMovie(120, null, AgeRating.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("releaseYear is required");
    }

    @Test
    void create_rejectsInvalidRuntime() {
        assertThatThrownBy(() -> createMovie(0, 2020, AgeRating.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("runtime must be between 1 and 1000");
    }

    @Test
    void create_requiresAgeRating() {
        assertThatThrownBy(() -> createMovie(120, 2020, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ageRating is required");
    }

    @Test
    void changeBasicInfo_usesSameValidationAndNormalization() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        movie.changeBasicInfo("  수정된 제목  ", 130, 2021, AgeRating.AGE_15);

        assertThat(movie.getTitle()).isEqualTo("수정된 제목");
        assertThat(movie.getRuntime()).isEqualTo(130);
        assertThat(movie.getReleaseYear()).isEqualTo(2021);
        assertThat(movie.getAgeRating()).isEqualTo(AgeRating.AGE_15);
    }

    @Test
    void addTrailer_ignoresDuplicateAfterNormalization() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        movie.addTrailer("trailer-key");
        movie.addTrailer("  trailer-key  ");

        assertThat(movie.getTrailers()).hasSize(1);
    }

    @Test
    void createMovieGenre_attachesBothSides() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        MovieGenre movieGenre = MovieGenre.create(movie, null, "  Action  ");

        assertThat(movieGenre.getMovie()).isSameAs(movie);
        assertThat(movie.getGenres()).containsExactly(movieGenre);
        assertThat(movieGenre.getRawText()).isEqualTo("Action");
        assertThat(movieGenre.getNormalizedText()).isEqualTo("action");
    }

    @Test
    void createMovieGenre_rejectsDuplicateNormalizedText() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);
        MovieGenre.create(movie, null, "Action");

        assertThatThrownBy(() -> MovieGenre.create(movie, null, "  action  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate movie genre");

        assertThat(movie.getGenres()).hasSize(1);
    }

    @Test
    void addMovieGenre_rejectsReassignmentToAnotherMovie() {
        Movie firstMovie = createMovie(120, 2020, AgeRating.ALL);
        Movie secondMovie = createMovie(120, 2021, AgeRating.ALL);
        MovieGenre movieGenre = MovieGenre.create(firstMovie, null, "Action");

        assertThatThrownBy(() -> secondMovie.addMovieGenre(movieGenre))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("movieGenre already belongs to another movie");

        assertThat(secondMovie.getGenres()).isEmpty();
    }

    private static Movie createMovie(int runtime, Integer releaseYear, AgeRating ageRating) {
        return Movie.create(
                "테스트 영화",
                runtime,
                releaseYear,
                "movie-key",
                null,
                List.of(),
                ageRating
        );
    }
}
