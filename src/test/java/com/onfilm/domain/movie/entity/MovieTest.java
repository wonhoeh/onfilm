package com.onfilm.domain.movie.entity;

import com.onfilm.domain.genre.entity.Genre;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieTest {

    private static final String TRAILER_KEY =
            "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000.mp4";

    @Test
    void create_validatesAndNormalizesFields() {
        Movie movie = Movie.create(
                "  인셉션  ",
                148,
                2010,
                "  movie-key  ",
                "   ",
                AgeRating.ALL
        );

        assertThat(movie.getTitle()).isEqualTo("인셉션");
        assertThat(movie.getRuntime()).isEqualTo(148);
        assertThat(movie.getReleaseYear()).isEqualTo(2010);
        assertThat(movie.getMovieUrl()).isEqualTo("movie-key");
        assertThat(movie.getThumbnailUrl()).isNull();
        assertThat(movie.getTrailers()).isEmpty();
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
    void addTrailer_normalizesUrlAndAttachesBothSides() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        Trailer trailer = movie.addTrailer("  " + TRAILER_KEY + "  ");

        assertThat(trailer.getStorageKey()).isEqualTo(TRAILER_KEY);
        assertThat(trailer.getMovie()).isSameAs(movie);
        assertThat(movie.getTrailers()).containsExactly(trailer);
    }

    @Test
    void addTrailer_rejectsBlankTooLongAndDuplicateUrl() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        assertThatThrownBy(() -> movie.addTrailer((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("trailerStorageKey is required");
        assertThatThrownBy(() -> movie.addTrailer("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("trailerStorageKey is required");
        assertThat(movie.addTrailer("a".repeat(Trailer.STORAGE_KEY_MAX_LENGTH)).getStorageKey())
                .hasSize(Trailer.STORAGE_KEY_MAX_LENGTH);
        assertThatThrownBy(() -> movie.addTrailer("a".repeat(Trailer.STORAGE_KEY_MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("trailerStorageKey is too long (max 512)");

        movie.addTrailer(TRAILER_KEY);

        assertThatThrownBy(() -> movie.addTrailer("  " + TRAILER_KEY + "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate trailer");
    }

    @Test
    void addTrailer_rejectsReassignmentToAnotherMovie() {
        Movie firstMovie = createMovie(120, 2020, AgeRating.ALL);
        Movie secondMovie = createMovie(120, 2021, AgeRating.ALL);
        Trailer trailer = firstMovie.addTrailer(TRAILER_KEY);

        assertThatThrownBy(() -> secondMovie.addTrailer(trailer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("trailer already belongs to another movie");

        assertThat(secondMovie.getTrailers()).isEmpty();
    }

    @Test
    void removeTrailer_detachesBothSides() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);
        Trailer trailer = movie.addTrailer(TRAILER_KEY);

        movie.removeTrailer(trailer);

        assertThat(movie.getTrailers()).isEmpty();
        assertThat(trailer.getMovie()).isNull();
        assertThatThrownBy(() -> movie.removeTrailer(trailer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("trailer does not belong to movie");
    }

    @Test
    void clearTrailers_detachesEveryTrailer() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);
        Trailer first = movie.addTrailer(TRAILER_KEY);
        Trailer second = movie.addTrailer(
                "movie/1/trailer/6ba7b810-9dad-41d1-80b4-00c04fd430c8.mp4"
        );

        movie.clearTrailers();

        assertThat(movie.getTrailers()).isEmpty();
        assertThat(first.getMovie()).isNull();
        assertThat(second.getMovie()).isNull();
    }

    @Test
    void createCustomMovieGenre_attachesBothSides() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        MovieGenre movieGenre = movie.addCustomGenre("  Action  ");

        assertThat(movieGenre.getMovie()).isSameAs(movie);
        assertThat(movie.getGenres()).containsExactly(movieGenre);
        assertThat(movieGenre.getRawText()).isEqualTo("Action");
        assertThat(movieGenre.getNormalizedText()).isEqualTo("action");
    }

    @Test
    void createCustomMovieGenre_rejectsDuplicateNormalizedText() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);
        movie.addCustomGenre("Action");

        assertThatThrownBy(() -> movie.addCustomGenre("  action  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate movie genre");

        assertThat(movie.getGenres()).hasSize(1);
    }

    @Test
    void addMovieGenre_rejectsReassignmentToAnotherMovie() {
        Movie firstMovie = createMovie(120, 2020, AgeRating.ALL);
        Movie secondMovie = createMovie(120, 2021, AgeRating.ALL);
        MovieGenre movieGenre = firstMovie.addCustomGenre("Action");

        assertThatThrownBy(() -> secondMovie.addMovieGenre(movieGenre))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("movieGenre already belongs to another movie");

        assertThat(secondMovie.getGenres()).isEmpty();
    }

    @Test
    void createStandardMovieGenre_usesStandardGenreValues() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);
        Genre genre = Genre.create("  Action  ");

        MovieGenre movieGenre = movie.addStandardGenre(genre);

        assertThat(movieGenre.getGenre()).isSameAs(genre);
        assertThat(movieGenre.getRawText()).isEqualTo("Action");
        assertThat(movieGenre.getNormalizedText()).isEqualTo("action");
        assertThat(movie.getGenres()).containsExactly(movieGenre);
    }

    @Test
    void createStandardMovieGenre_requiresGenre() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);

        assertThatThrownBy(() -> movie.addStandardGenre(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("genre is required");
    }

    @Test
    void removeMovieGenre_detachesBothSidesAndCollectionsAreReadOnly() {
        Movie movie = createMovie(120, 2020, AgeRating.ALL);
        MovieGenre movieGenre = movie.addCustomGenre("Action");

        assertThatThrownBy(() -> movie.getGenres().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> movie.getTrailers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> movie.getLikes().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        movie.removeMovieGenre(movieGenre);

        assertThat(movie.getGenres()).isEmpty();
        assertThat(movieGenre.getMovie()).isNull();
    }

    private static Movie createMovie(int runtime, Integer releaseYear, AgeRating ageRating) {
        return Movie.create(
                "테스트 영화",
                runtime,
                releaseYear,
                "movie-key",
                null,
                ageRating
        );
    }
}
