package com.onfilm.domain.genre.repository;

import com.onfilm.domain.genre.entity.Genre;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class GenreRepositoryTest {

    @Autowired
    private GenreRepository genreRepository;

    @Test
    void findActiveByPrefix_returnsExactMatchFirstAndAppliesLimit() {
        genreRepository.save(Genre.create("Action Thriller"));
        genreRepository.save(Genre.create("Action"));
        genreRepository.save(Genre.create("Action Comedy"));
        for (int index = 0; index < 10; index++) {
            genreRepository.save(Genre.create("Action Genre " + index));
        }

        List<Genre> result = genreRepository.findActiveByPrefix(
                "action",
                PageRequest.of(0, 10)
        );

        assertThat(result).hasSize(10);
        assertThat(result.get(0).getName()).isEqualTo("Action");
        assertThat(result).extracting(Genre::getNormalized)
                .allMatch(normalized -> normalized.startsWith("action"));
    }

    @Test
    void findActiveByIds_returnsSelectedStandardGenres() {
        Genre action = genreRepository.save(Genre.create("Action"));
        Genre drama = genreRepository.save(Genre.create("Drama"));
        genreRepository.save(Genre.create("Comedy"));

        List<Genre> result = genreRepository.findActiveByIds(
                List.of(action.getId(), drama.getId())
        );

        assertThat(result).extracting(Genre::getName)
                .containsExactlyInAnyOrder("Action", "Drama");
    }

    @Test
    void findActiveByNormalizedValues_returnsMatchingStandardGenres() {
        genreRepository.save(Genre.create("Action"));
        genreRepository.save(Genre.create("Drama"));
        genreRepository.save(Genre.create("Comedy"));

        List<Genre> result = genreRepository.findActiveByNormalizedValues(
                List.of("action", "drama", "unknown")
        );

        assertThat(result).extracting(Genre::getName)
                .containsExactlyInAnyOrder("Action", "Drama");
    }
}
