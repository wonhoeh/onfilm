package com.onfilm.domain.genre.service;

import com.onfilm.domain.common.util.TextNormalizer;
import com.onfilm.domain.genre.dto.GenreAutocompleteResponse;
import com.onfilm.domain.genre.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GenreService {

    private static final Pageable AUTOCOMPLETE_LIMIT = PageRequest.of(0, 10);

    private final GenreRepository genreRepository;

    public List<GenreAutocompleteResponse> autocomplete(String query) {
        String normalized = TextNormalizer.textNormalizer(query);
        if (normalized.isBlank()) {
            return List.of();
        }

        return genreRepository.findActiveByPrefix(normalized, AUTOCOMPLETE_LIMIT)
                .stream()
                .map(GenreAutocompleteResponse::from)
                .toList();
    }
}
