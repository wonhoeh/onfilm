package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createMovie(CreateMovieRequest request) {
        Movie movie = request.toEntity();

        Long userId = SecurityUtil.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Person person = user.getPerson();

        MoviePerson moviePerson = MoviePerson.create(movie, person, request.getRole(), request.getCastType(), request.getCharacterName());
        movie.addMoviePerson(moviePerson);
        attachGenresHybrid(movie, request.getRawGenreTexts());

        Movie saved = movieRepository.save(movie);
        return saved.getId();
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
