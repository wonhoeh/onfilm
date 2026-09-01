package com.onfilm.domain.genre;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.entity.GenreName;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GenreReferenceDataIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private GenreRepository genreRepository;

    @Test
    void flywayLoadsStandardGenresWithStableIdsAndNormalizedNames() {
        List<Genre> genres = genreRepository.findAll();

        assertThat(genres)
                .hasSize(19)
                .extracting(Genre::getName)
                .contains("액션", "드라마", "코미디", "SF", "애니메이션");
        assertThat(genres)
                .allSatisfy(genre -> assertThat(genre.getNormalized())
                        .isEqualTo(GenreName.normalize(genre.getName())));
        assertThat(genres)
                .filteredOn(genre -> genre.getName().equals("액션"))
                .singleElement()
                .extracting(Genre::getId)
                .isEqualTo(1L);

        assertThat(genreRepository.findActiveByPrefix(
                "스",
                PageRequest.of(0, 10)
        )).extracting(Genre::getName)
                .containsExactly("스릴러", "스포츠");
    }
}
