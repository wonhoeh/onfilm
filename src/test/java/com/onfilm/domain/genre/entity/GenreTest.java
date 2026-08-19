package com.onfilm.domain.genre.entity;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenreTest {

    @Test
    void create_sanitizesDisplayNameAndNormalizedValue() {
        Genre genre = Genre.create("  ###  ＡＣＴＩＯＮ   ＣＯＭＥＤＹ  ");

        assertThat(genre.getName()).isEqualTo("ACTION COMEDY");
        assertThat(genre.getNormalized()).isEqualTo("action comedy");
        assertThat(genre.isActive()).isTrue();
    }

    @Test
    void create_rejectsNullBlankAndHashOnlyNames() {
        assertRequired(null);
        assertRequired("   ");
        assertRequired("###");
        assertRequired("###   ");
    }

    @Test
    void create_acceptsMaximumLengthAndRejectsLongerName() {
        assertThat(Genre.create("a".repeat(GenreName.MAX_LENGTH)).getName())
                .hasSize(GenreName.MAX_LENGTH);

        assertThatThrownBy(() -> Genre.create("a".repeat(GenreName.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("genre name is too long (max 60)");

        assertThatThrownBy(() -> Genre.create("İ".repeat(GenreName.MAX_LENGTH)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("normalized genre name is too long (max 60)");
    }

    @Test
    void normalizationDoesNotDependOnDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(Genre.create("I").getNormalized()).isEqualTo("i");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void activateAndDeactivateAreIdempotent() {
        Genre genre = Genre.create("Action");

        genre.deactivate();
        genre.deactivate();
        assertThat(genre.isActive()).isFalse();

        genre.activate();
        genre.activate();
        assertThat(genre.isActive()).isTrue();
    }

    private static void assertRequired(String name) {
        assertThatThrownBy(() -> Genre.create(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("genre name is required");
    }
}
