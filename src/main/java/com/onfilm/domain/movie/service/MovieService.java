package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.common.error.exception.UserNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertResponse;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final MovieGenreFactory movieGenreFactory;
    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;

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

        MoviePerson mp = movie.getMoviePeople().get(movie.getMoviePeople().size() - 1);
        Integer max = moviePersonRepository.findMaxSortOrderByPersonId(person.getId());
        mp.updateSortOrder(max == null ? 0 : max + 1);

        // 장르는 도메인 서비스(팩토리)로만 부착
        movieGenreFactory.attachGenre(movie, request.getRawGenreTexts());

        Movie saved = movieRepository.save(movie);
        return saved.getId();
    }

    @Transactional
    public FilmographyUpsertResponse upsertFilmography(String publicId, FilmographyUpsertRequest request) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        List<FilmographyUpsertRequest.Item> items =
                request == null || request.items() == null ? List.of() : request.items();

        List<MoviePerson> existing = moviePersonRepository.findFilmographyByPersonId(person.getId());
        var mpByMovieId = existing.stream()
                .collect(java.util.stream.Collectors.toMap(mp -> mp.getMovie().getId(), mp -> mp));

        java.util.Set<Long> keepMovieIds = new java.util.HashSet<>();
        java.util.List<FilmographyUpsertResponse.Item> results = new java.util.ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            FilmographyUpsertRequest.Item item = items.get(i);
            if (item == null) continue;

            Long movieId = item.movieId();
            if (movieId != null && mpByMovieId.containsKey(movieId)) {
                MoviePerson mp = mpByMovieId.get(movieId);
                Movie movie = mp.getMovie();

                movie.updateBasic(
                        item.title(),
                        item.runtime(),
                        item.releaseYear(),
                        item.ageRating()
                );

                movie.clearGenres();
                movieGenreFactory.attachGenre(movie, item.rawGenreTexts());

                mp.updateRole(item.role(), item.castType(), item.characterName());
                mp.updateSortOrder(i);

                keepMovieIds.add(movieId);
                results.add(new FilmographyUpsertResponse.Item(item.clientKey(), movieId));
                continue;
            }

            Movie movie = Movie.create(
                    item.title(),
                    item.runtime(),
                    item.releaseYear(),
                    "pending",
                    null,
                    List.of(),
                    item.ageRating()
            );

            MoviePerson.create(
                    movie,
                    person,
                    item.role(),
                    item.castType(),
                    item.characterName()
            );

            MoviePerson createdMp = movie.getMoviePeople().get(movie.getMoviePeople().size() - 1);
            createdMp.updateSortOrder(i);

            movieGenreFactory.attachGenre(movie, item.rawGenreTexts());

            Movie saved = movieRepository.save(movie);
            Long savedId = saved.getId();
            keepMovieIds.add(savedId);
            results.add(new FilmographyUpsertResponse.Item(item.clientKey(), savedId));
        }

        List<MoviePerson> toDelete = existing.stream()
                .filter(mp -> !keepMovieIds.contains(mp.getMovie().getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            moviePersonRepository.deleteAll(toDelete);
        }

        return new FilmographyUpsertResponse(results);
    }
}
