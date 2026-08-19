package com.onfilm.domain.movie.dto;

import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.entity.*;

import java.util.List;

public record MovieCardResponse(
        Long movieId,
        String title,
        List<MovieGenreResponse> genres,
        int runtime,
        Integer releaseYear,
        AgeRating ageRating,
        String movieUrl,
        String thumbnailUrl,
        String trailerUrl,

        PersonRole personRole,
        CastType castType,
        String characterName,
        boolean isPrivate) {

    public static MovieCardResponse from(
            MoviePerson mp,
            StorageService storageService
    ) {
        Movie m = mp.getMovie();

        List<MovieGenreResponse> genres = (m.getGenres() == null)
                ? List.of()
                : m.getGenres().stream()
                .map(MovieGenreResponse::from)
                .toList();

        String trailer = (m.getTrailers() == null || m.getTrailers().isEmpty())
                ? null
                : m.getTrailers().stream()
                .map(Trailer::getStorageKey)
                .filter(key -> key != null && !key.isBlank())
                .map(storageService::toPublicUrl)
                .findFirst()
                .orElse(null);

        return new MovieCardResponse(
                m.getId(),
                m.getTitle(),
                genres,
                m.getRuntime(),
                m.getReleaseYear(),
                m.getAgeRating(),
                m.getMovieUrl(),
                m.getThumbnailUrl(),
                trailer,

                mp.getRole(),
                mp.getCastType(),
                mp.getCharacterName(),
                mp.isPrivate()
        );
    }
}
