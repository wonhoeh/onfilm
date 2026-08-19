package com.onfilm.domain.movie.service;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.entity.GenreName;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.dto.MovieGenreRequest;
import com.onfilm.domain.movie.entity.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class MovieGenreNormalizer {

    private final GenreRepository genreRepository;

    public void attachGenre(Movie movie, List<MovieGenreRequest> requests) {
        if (movie == null) {
            throw new IllegalArgumentException("movie is required");
        }
        if (requests == null || requests.isEmpty()) {
            return;
        }

        validateRequests(requests);

        Map<Long, Genre> genreById = findSelectedStandardGenres(requests);
        Map<String, Genre> genreByNormalized = findMatchingStandardGenres(requests);
        Map<String, ResolvedGenre> resolvedByNormalized = new LinkedHashMap<>();

        for (MovieGenreRequest request : requests) {
            ResolvedGenre resolved = request.genreId() != null
                    ? resolveStandard(request.genreId(), genreById)
                    : resolveCustom(request.customText(), genreByNormalized);

            resolvedByNormalized.putIfAbsent(resolved.normalized(), resolved);
        }

        resolvedByNormalized.values()
                .forEach(resolved -> attachResolvedGenre(movie, resolved));
    }

    private static void validateRequests(List<MovieGenreRequest> requests) {
        for (MovieGenreRequest request : requests) {
            if (request == null || !request.isSourceValid()) {
                throw new IllegalArgumentException(
                        "genreId or customText must be provided exclusively"
                );
            }
        }
    }

    private Map<Long, Genre> findSelectedStandardGenres(List<MovieGenreRequest> requests) {
        List<Long> genreIds = requests.stream()
                .map(MovieGenreRequest::genreId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (genreIds.isEmpty()) {
            return Map.of();
        }

        return genreRepository.findActiveByIds(genreIds).stream()
                .collect(Collectors.toMap(Genre::getId, Function.identity()));
    }

    private Map<String, Genre> findMatchingStandardGenres(
            List<MovieGenreRequest> requests
    ) {
        List<String> normalizedValues = requests.stream()
                .filter(MovieGenreRequest::hasCustomText)
                .map(MovieGenreRequest::customText)
                .map(GenreName::normalize)
                .filter(normalized -> !normalized.isBlank())
                .distinct()
                .toList();

        if (normalizedValues.isEmpty()) {
            return Map.of();
        }

        return genreRepository.findActiveByNormalizedValues(normalizedValues)
                .stream()
                .collect(Collectors.toMap(
                        Genre::getNormalized,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private static ResolvedGenre resolveStandard(
            Long genreId,
            Map<Long, Genre> genreById
    ) {
        Genre genre = genreById.get(genreId);
        if (genre == null) {
            throw new IllegalArgumentException(
                    "active genre not found: " + genreId
            );
        }

        return new ResolvedGenre(
                genre,
                null,
                genre.getNormalized()
        );
    }

    private static ResolvedGenre resolveCustom(
            String customText,
            Map<String, Genre> genreByNormalized
    ) {
        GenreName value = GenreName.from(customText);
        Genre matchedGenre = genreByNormalized.get(value.normalized());

        if (matchedGenre != null) {
            return new ResolvedGenre(
                    matchedGenre,
                    null,
                    matchedGenre.getNormalized()
            );
        }

        return new ResolvedGenre(
                null,
                value.displayName(),
                value.normalized()
        );
    }

    private static void attachResolvedGenre(
            Movie movie,
            ResolvedGenre resolved
    ) {
        if (resolved.genre() != null) {
            movie.addStandardGenre(resolved.genre());
            return;
        }

        movie.addCustomGenre(resolved.customText());
    }

    private record ResolvedGenre(
            Genre genre,
            String customText,
            String normalized
    ) {}
}
