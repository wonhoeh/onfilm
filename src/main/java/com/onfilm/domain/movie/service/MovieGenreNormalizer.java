package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.util.TextNormalizer;
import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class MovieGenreNormalizer {

    private final GenreRepository genreRepository;

    public void attachGenre(Movie movie, List<String> rawGenreTexts) {
        if (movie == null) throw new IllegalArgumentException("movie is required");
        if (rawGenreTexts == null || rawGenreTexts.isEmpty()) return;

        // 1) 입력 정리 (일단 리스트로 만들기)
        List<RawAndNormalized> prepared = rawGenreTexts.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(raw -> new RawAndNormalized(raw, TextNormalizer.textNormalizer(raw)))
                .filter(rn -> rn.normalized() != null && !rn.normalized().isBlank())
                .toList();

        // 2) normalized 기준 중복 제거 + 순서 유지
        List<RawAndNormalized> inputs = distinctByNormalized(prepared);
        if (inputs.isEmpty()) return;

        // 2) DB Genre 매칭 (활성만)
        List<String> normalizedList = inputs.stream()
                .map(RawAndNormalized::normalized)
                .toList();

        List<Genre> found = genreRepository.findByNormalizedInAndIsActiveTrue(normalizedList);

        Map<String, Genre> genreByNormalized = found.stream()
                .collect(Collectors.toMap(Genre::getNormalized, Function.identity(), (a, b) -> a));

        // 3) MovieGenre 생성 + Movie에 추가(중복 방지는 Movie에서)
        for (RawAndNormalized input : inputs) {
            Genre matched = genreByNormalized.get(input.normalized());
            MovieGenre mg = MovieGenre.create(movie, matched, input.raw());
            movie.addMovieGenre(mg);
        }
    }

    private static List<RawAndNormalized> distinctByNormalized(List<RawAndNormalized> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();

        Map<String, RawAndNormalized> map = new LinkedHashMap<>();
        for (RawAndNormalized rn : inputs) {
            if (rn == null) continue;
            map.putIfAbsent(rn.normalized(), rn); // ✅ 이미 있으면(중복) 무시, 없으면 추가
        }
        return new ArrayList<>(map.values()); // ✅ 입력 순서 유지
    }

    private record RawAndNormalized(String raw, String normalized) {}
}
