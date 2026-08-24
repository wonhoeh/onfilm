package com.onfilm.domain.movie.service;

import com.onfilm.domain.common.error.exception.PersonNotFoundException;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.MovieGenreResponse;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.domain.movie.repository.MovieGenreRepository;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.repository.TrailerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FilmographyQueryService {

    private final MoviePersonRepository moviePersonRepository;
    private final MovieGenreRepository movieGenreRepository;
    private final TrailerRepository trailerRepository;
    private final PersonRepository personRepository;
    private final StorageService storageService;
    private final CurrentPersonProvider currentPersonProvider;

    public List<MovieCardResponse> findVisibleFilmography(String publicId) {
        Person person = personRepository.findByPublicId(publicId)
                .orElseThrow(() -> new PersonNotFoundException(publicId));
        boolean owner = currentPersonProvider.isCurrentPerson(person.getId());
        if (person.isFilmographyPrivate() && !owner) {
            return List.of();
        }
        List<MoviePerson> moviePeople = moviePersonRepository.findFilmographyByPersonId(person.getId());
        if (moviePeople.isEmpty()) {
            return List.of();
        }
        List<Long> movieIds = moviePeople.stream()
                .map(item -> item.getMovie().getId())
                .distinct()
                .toList();
        Map<Long, List<MovieGenreResponse>> genresByMovieId = movieGenreRepository
                .findAllByMovieIds(movieIds)
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getMovie().getId(),
                        Collectors.mapping(MovieGenreResponse::from, Collectors.toList())
                ));
        Map<Long, String> trailerUrlByMovieId = trailerRepository.findAllByMovieIds(movieIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getMovie().getId()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> trailerUrl(entry.getValue())));

        return moviePeople.stream()
                .filter(item -> owner || !item.isPrivate())
                .map(item -> toResponse(
                        item,
                        genresByMovieId.getOrDefault(item.getMovie().getId(), List.of()),
                        trailerUrlByMovieId.getOrDefault(item.getMovie().getId(), "")
                ))
                .toList();
    }

    private MovieCardResponse toResponse(
            MoviePerson moviePerson,
            List<MovieGenreResponse> genres,
            String trailerUrl
    ) {
        Movie movie = moviePerson.getMovie();
        return new MovieCardResponse(
                movie.getId(), movie.getTitle(), genres, movie.getRuntime(), movie.getReleaseYear(),
                movie.getAgeRating(), movie.getMovieUrl(), movie.getThumbnailUrl(), trailerUrl,
                moviePerson.getRole(), moviePerson.getCastType(), moviePerson.getCharacterName(),
                moviePerson.isPrivate()
        );
    }

    private String trailerUrl(List<Trailer> trailers) {
        return trailers.stream()
                .map(Trailer::getStorageKey)
                .filter(key -> key != null && !key.isBlank())
                .map(storageService::toPublicUrl)
                .findFirst()
                .orElse("");
    }
}
