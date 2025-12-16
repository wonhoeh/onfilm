package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
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

    public Long createMovie(CreateMovieRequest request, String thumbnailUrl, String movieUrl) {
        // 1) Movie 기본 생성 (DB 조회 필요 없는 값만)
        Movie movie = Movie.create(
                request.getTitle(),
                request.getRuntime(),
                request.getAgeRating(),
                request.getReleaseYear(),
                request.getSynopsis(),
                movieUrl,
                thumbnailUrl,
                request.getRawGenreTexts()
        );

        // 2) 장르 하이브리드 매칭 후 MovieGenre 생성/연결
        attachGenresHybrid(movie, request.getRawGenreTexts());

        // 3) 저장
        Movie saved = movieRepository.save(movie);
        return saved.getId();
    }

    /**
     * 하이브리드 정책:
     * - DB에 있는 장르면 MovieGenre.genre에 연결
     * - 없으면 MovieGenre.genre = null (raw/normalized만 저장)
     */
    private void attachGenresHybrid(Movie movie, List<String> rawGenreTexts) {
        if (rawGenreTexts == null || rawGenreTexts.isEmpty()) return;

        // (A) 입력값 정리/정규화 + 빈값 제거 + (옵션) 중복 제거
        List<RawAndNormalized> inputs = rawGenreTexts.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(raw -> new RawAndNormalized(raw, TextNormalizer.normalizeTag(raw)))
                // normalized 기준 중복 제거(원하면 제거)
                .collect(collectDistinctByNormalized());

        if (inputs.isEmpty()) return;

        // (B) normalized 값들로 DB 장르 한번에 조회 (bulk)
        List<String> normalizedList = inputs.stream()
                .map(RawAndNormalized::normalized)
                .distinct()
                .toList();

        List<Genre> found = genreRepository.findByNormalizedInAndIsActiveTrue(normalizedList);

        // (C) 빠른 매칭을 위해 Map 구성
        Map<String, Genre> genreByNormalized = found.stream()
                .collect(Collectors.toMap(Genre::getNormalized, Function.identity()));

        // (D) MovieGenre 생성해서 Movie에 연결
        for (RawAndNormalized input : inputs) {
            Genre matched = genreByNormalized.get(input.normalized());

            // ✅ matched == null 이면 genreId 없이 raw/normalized만 저장
            MovieGenre link = MovieGenre.create(movie, matched, input.raw(), input.normalized());
        }
    }

    // ====== 유틸(내부 record/메서드) ======

    private record RawAndNormalized(String raw, String normalized) {}

    private static Collector<RawAndNormalized, ?, List<RawAndNormalized>> collectDistinctByNormalized() {
        // normalized 기준으로 첫 번째 raw만 남김
        return Collectors.collectingAndThen(
                Collectors.toMap(
                        RawAndNormalized::normalized,
                        Function.identity(),
                        (a, b) -> a, // 충돌 시 첫 값 유지
                        LinkedHashMap::new
                ),
                m -> new ArrayList<>(m.values())
        );
    }

}
