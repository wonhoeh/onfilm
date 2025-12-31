package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.TextNormalizer;
import com.onfilm.domain.common.error.exception.PersonNotFoundException;
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
    private final MovieGenreFactory movieGenreFactory;

    @Transactional
    public Long createMovie(CreateMovieRequest request) {
        Movie movie = Movie.create(
                request.getTitle(),
                request.getRuntime(),
                request.getReleaseYear(),
                request.getMovieUrl(),
                request.getThumbnailUrl(),
                request.getTrailerUrls(),
                request.getAgeRating()
        );

        Long userId = SecurityUtil.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Person person = user.getPerson();
        if (person == null) {
            throw new PersonNotFoundException(userId);
        }

        // MoviePerson은 Movie에 소속시키는 생성 규칙을 create()에 캡슐화
        MoviePerson.create(
                movie,
                person,
                request.getRole(),
                request.getCastType(),
                request.getCharacterName()
        );

        // 장르는 도메인 서비스(팩토리)로만 부착
        movieGenreFactory.attachHybrid(movie, request.getRawGenreTexts());

        Movie saved = movieRepository.save(movie);
        return saved.getId();
    }
}
