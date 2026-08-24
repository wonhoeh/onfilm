package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.FilmographyUpsertRequest;
import com.onfilm.domain.movie.dto.FilmographyUpsertResponse;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FilmographyCommandService {

    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final MovieGenreNormalizer movieGenreNormalizer;
    private final CurrentPersonProvider currentPersonProvider;

    public FilmographyUpsertResponse replace(String publicId, FilmographyUpsertRequest request) {
        Person person = currentPersonProvider.getRequired(publicId);
        List<MoviePerson> existing = moviePersonRepository.findFilmographyByPersonId(person.getId());
        var moviePersonByMovieId = existing.stream()
                .collect(Collectors.toMap(item -> item.getMovie().getId(), item -> item));
        Set<Long> keepMovieIds = new HashSet<>();
        List<FilmographyUpsertResponse.Item> results = new ArrayList<>();

        for (int index = 0; index < request.items().size(); index++) {
            FilmographyUpsertRequest.Item item = request.items().get(index);
            Long movieId = item.movieId();
            if (movieId != null && moviePersonByMovieId.containsKey(movieId)) {
                MoviePerson moviePerson = moviePersonByMovieId.get(movieId);
                updateExisting(moviePerson, item, index);
                keepMovieIds.add(movieId);
                results.add(new FilmographyUpsertResponse.Item(item.clientKey(), movieId));
                continue;
            }

            Movie saved = createNew(person, item, index);
            keepMovieIds.add(saved.getId());
            results.add(new FilmographyUpsertResponse.Item(item.clientKey(), saved.getId()));
        }

        existing.stream()
                .filter(item -> !keepMovieIds.contains(item.getMovie().getId()))
                .forEach(item -> item.getMovie().removeMoviePerson(item));
        return new FilmographyUpsertResponse(results);
    }

    public void changeItemPrivacy(String publicId, Long movieId, boolean isPrivate) {
        Person person = currentPersonProvider.getRequired(publicId);
        MoviePerson moviePerson = moviePersonRepository.findByPersonIdAndMovieId(person.getId(), movieId);
        if (moviePerson == null) {
            throw new IllegalArgumentException("filmography item not found");
        }
        moviePerson.changePrivacy(isPrivate);
    }

    public void changeFilmographyPrivacy(String publicId, boolean isPrivate) {
        currentPersonProvider.getRequired(publicId).changeFilmographyPrivate(isPrivate);
    }

    private void updateExisting(
            MoviePerson moviePerson,
            FilmographyUpsertRequest.Item item,
            int sortOrder
    ) {
        Movie movie = moviePerson.getMovie();
        movie.changeBasicInfo(item.title(), item.runtime(), item.releaseYear(), item.ageRating());
        movie.clearGenres();
        movieGenreNormalizer.attachGenre(movie, item.genres());
        moviePerson.changeRole(item.role(), item.castType(), item.characterName());
        moviePerson.changeSortOrder(sortOrder);
        moviePerson.changePrivacy(item.isPrivate());
    }

    private Movie createNew(
            Person person,
            FilmographyUpsertRequest.Item item,
            int sortOrder
    ) {
        Movie movie = Movie.create(
                item.title(), item.runtime(), item.releaseYear(), "pending", null, item.ageRating()
        );
        MoviePerson moviePerson = movie.addMoviePerson(
                person, item.role(), item.castType(), item.characterName()
        );
        moviePerson.changeSortOrder(sortOrder);
        moviePerson.changePrivacy(item.isPrivate());
        movieGenreNormalizer.attachGenre(movie, item.genres());
        return movieRepository.save(movie);
    }
}
