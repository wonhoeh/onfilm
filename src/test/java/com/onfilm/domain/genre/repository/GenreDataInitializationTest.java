package com.onfilm.domain.genre.repository;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.entity.GenreName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class GenreDataInitializationTest {

    @Autowired
    private GenreRepository genreRepository;

    @Test
    void dataSql_loadsStandardGenres() {
        assertThat(genreRepository.findAll())
                .hasSize(19)
                .extracting(Genre::getName)
                .contains("액션", "드라마", "코미디", "SF", "애니메이션");

        assertThat(genreRepository.findAll())
                .allSatisfy(genre -> assertThat(genre.getNormalized())
                        .isEqualTo(GenreName.normalize(genre.getName())));

        List<Genre> autocompleteResult = genreRepository.findActiveByPrefix(
                "스",
                PageRequest.of(0, 10)
        );

        assertThat(autocompleteResult)
                .extracting(Genre::getName)
                .containsExactly("스릴러", "스포츠");
    }
}
