package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.MovieDetailResponse;
import com.onfilm.domain.movie.dto.MovieExtraInfoResponse;
import com.onfilm.domain.movie.dto.MovieResponse;
import com.onfilm.domain.movie.dto.MovieThumbnailResponse;
import com.onfilm.domain.movie.dto.MovieWatchResponse;
import com.onfilm.domain.movie.dto.UpdateMovieRequest;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    public Long createMovie(CreateMovieRequest request) {
        Movie movie = Movie.create(
                request.getTitle(),
                request.getRuntime(),
                request.getAgeRating(),
                request.getReleaseYear(),
                request.getSynopsis(),
                null,
                null,
                request.getRawGenreTexts()
        );

        attachGenresHybrid(movie, request.getRawGenreTexts());

        Movie saved = movieRepository.save(movie);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<MovieThumbnailResponse> findAllWithThumbnailUrl() {
        return movieRepository.findAll().stream()
                .map(MovieThumbnailResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MovieThumbnailResponse> findAllByReleaseDate(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return movieRepository.findAllByOrderByReleaseDateDesc(pageable)
                .map(MovieThumbnailResponse::new);
    }

    public void deleteMovie(Long movieId) {
        movieRepository.deleteById(movieId);
    }

    @Transactional(readOnly = true)
    public MovieResponse findMovieByIdV1(Long id) {
        return new MovieResponse(getMovie(id));
    }

    @Transactional(readOnly = true)
    public MovieResponse findMovieByIdV3(Long id) {
        return new MovieResponse(getMovie(id));
    }

    @Transactional(readOnly = true)
    public MovieCardResponse findMovieCardById(Long id) {
        return new MovieCardResponse(getMovie(id));
    }

    @Transactional(readOnly = true)
    public MovieDetailResponse findMovieDetailById(Long id) {
        return new MovieDetailResponse(getMovie(id));
    }

    @Transactional(readOnly = true)
    public MovieExtraInfoResponse findMovieExtraInfoById(Long id) {
        return new MovieExtraInfoResponse(getMovie(id));
    }

    public void updateMovie(Long movieId, UpdateMovieRequest request) {
        Movie movie = getMovie(movieId);
        movie.updateFrom(request);
    }

    @Transactional(readOnly = true)
    public MovieWatchResponse watchMovie(Long movieId) {
        return new MovieWatchResponse(getMovie(movieId));
    }

    private Movie getMovie(Long id) {
        return movieRepository.findById(id).orElseThrow(MovieNotFoundException::new);
    }

    private void attachGenresHybrid(Movie movie, List<String> rawGenreTexts) {
        if (rawGenreTexts == null || rawGenreTexts.isEmpty()) return;

        List<RawAndNormalized> inputs = rawGenreTexts.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(raw -> new RawAndNormalized(raw, TextNormalizer.normalizeTag(raw)))
                .collect(collectDistinctByNormalized());

        if (inputs.isEmpty()) return;

        List<String> normalizedList = inputs.stream()
                .map(RawAndNormalized::normalized)
                .distinct()
                .toList();

        List<Genre> found = genreRepository.findByNormalizedInAndIsActiveTrue(normalizedList);

        Map<String, Genre> genreByNormalized = found.stream()
                .collect(Collectors.toMap(Genre::getNormalized, Function.identity()));

        for (RawAndNormalized input : inputs) {
            Genre matched = genreByNormalized.get(input.normalized());
            MovieGenre.create(movie, matched, input.raw(), input.normalized());
        }
    }

    private record RawAndNormalized(String raw, String normalized) {}

    private static Collector<RawAndNormalized, ?, List<RawAndNormalized>> collectDistinctByNormalized() {
        return Collectors.collectingAndThen(
                Collectors.toMap(
                        RawAndNormalized::normalized,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ),
                m -> new ArrayList<>(m.values())
        );
    }
}
