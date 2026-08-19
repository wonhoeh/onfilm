package com.onfilm.domain.genre.service;

import com.onfilm.domain.genre.dto.GenreAutocompleteResponse;
import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GenreServiceTest {

    @Mock
    private GenreRepository genreRepository;

    @InjectMocks
    private GenreService genreService;

    @Test
    void autocomplete_normalizesQueryAndLimitsResultsToTen() {
        Genre action = Genre.create("Action");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        given(genreRepository.findActiveByPrefix(
                eq("action"),
                any(Pageable.class)
        )).willReturn(List.of(action));

        List<GenreAutocompleteResponse> result = genreService.autocomplete("  # ACTION  ");

        verify(genreRepository)
                .findActiveByPrefix(
                        eq("action"),
                        pageableCaptor.capture()
                );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(result).extracting(GenreAutocompleteResponse::name)
                .containsExactly("Action");
    }

    @Test
    void autocomplete_returnsEmptyResultForBlankQuery() {
        assertThat(genreService.autocomplete("   ")).isEmpty();
        assertThat(genreService.autocomplete(null)).isEmpty();

        verifyNoInteractions(genreRepository);
    }

    @Test
    void autocomplete_rejectsTooLongQuery() {
        assertThatThrownBy(() -> genreService.autocomplete("a".repeat(61)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("genre name is too long (max 60)");

        verifyNoInteractions(genreRepository);
    }
}
