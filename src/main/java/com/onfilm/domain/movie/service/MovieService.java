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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final MovieGenreNormalizer movieGenreNormalizer;
    private final MoviePersonRepository moviePersonRepository;
    private final PersonRepository personRepository;

    @Transactional
    public Long createMovie(CreateMovieRequest request) {
        Movie movie = Movie.create(
                request.title(),
                request.runtime(),
                request.releaseYear(),
                request.movieUrl(),
                request.thumbnailUrl(),
                request.ageRating()
        );

        Long userId = SecurityUtil.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Person person = user.getPerson();
        if (person == null) {
            throw new PersonNotFoundException(userId);
        }

        MoviePerson moviePerson = movie.addMoviePerson(
                person,
                request.role(),
                request.castType(),
                request.characterName()
        );

        Integer max = moviePersonRepository.findMaxSortOrderByPersonId(person.getId());
        moviePerson.changeSortOrder(max == null ? 0 : max + 1);

        // 장르는 도메인 서비스(팩토리)로만 부착
        movieGenreNormalizer.attachGenre(movie, request.genres());

        Movie saved = movieRepository.save(movie);
        return saved.getId();
    }

    @Transactional
    public FilmographyUpsertResponse upsertFilmography(String publicId, FilmographyUpsertRequest request) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));

        List<FilmographyUpsertRequest.Item> items = request.items();

        List<MoviePerson> existing = moviePersonRepository.findFilmographyByPersonId(person.getId());
        var mpByMovieId = existing.stream()
                .collect(Collectors.toMap(mp -> mp.getMovie().getId(), mp -> mp));

        Set<Long> keepMovieIds = new HashSet<>();
        List<FilmographyUpsertResponse.Item> results = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            FilmographyUpsertRequest.Item item = items.get(i);
            Long movieId = item.movieId();
            if (movieId != null && mpByMovieId.containsKey(movieId)) {
                MoviePerson mp = mpByMovieId.get(movieId);
                Movie movie = mp.getMovie();

                movie.changeBasicInfo(
                        item.title(),
                        item.runtime(),
                        item.releaseYear(),
                        item.ageRating()
                );

                movie.clearGenres();
                movieGenreNormalizer.attachGenre(movie, item.genres());

                mp.changeRole(item.role(), item.castType(), item.characterName());
                mp.changeSortOrder(i);
                mp.changePrivacy(item.isPrivate());

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
                    item.ageRating()
            );

            MoviePerson createdMoviePerson = movie.addMoviePerson(
                    person,
                    item.role(),
                    item.castType(),
                    item.characterName()
            );

            createdMoviePerson.changeSortOrder(i);
            createdMoviePerson.changePrivacy(item.isPrivate());

            movieGenreNormalizer.attachGenre(movie, item.genres());

            Movie saved = movieRepository.save(movie);
            Long savedId = saved.getId();
            keepMovieIds.add(savedId);
            results.add(new FilmographyUpsertResponse.Item(item.clientKey(), savedId));
        }

        List<MoviePerson> toDelete = existing.stream()
                .filter(mp -> !keepMovieIds.contains(mp.getMovie().getId()))
                .toList();
        for (MoviePerson moviePerson : toDelete) {
            moviePerson.getMovie().removeMoviePerson(moviePerson);
        }

        return new FilmographyUpsertResponse(results);
    }

    @Transactional
    public void updateFilmographyItemPrivacy(Long movieId, boolean isPrivate) {
        Long personId = findCurrentPersonId();
        if (movieId == null) throw new IllegalArgumentException("movieId is required");

        MoviePerson mp = moviePersonRepository.findByPersonIdAndMovieId(personId, movieId);
        if (mp == null) throw new IllegalArgumentException("filmography item not found");
        mp.changePrivacy(isPrivate);
    }

    private Long findCurrentPersonId() {
        String principal = SecurityUtil.currentPrincipal();
        Long userId;
        try {
            userId = Long.valueOf(principal);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("INVALID_PRINCIPAL");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND"));
        if (user.getPerson() == null) {
            throw new IllegalStateException("PERSON_NOT_LINKED");
        }
        return user.getPerson().getId();
    }
}
