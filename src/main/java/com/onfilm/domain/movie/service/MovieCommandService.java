package com.onfilm.domain.movie.service;

import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MovieCommandService {

    private final MovieRepository movieRepository;
    private final MoviePersonRepository moviePersonRepository;
    private final MovieGenreNormalizer movieGenreNormalizer;
    private final CurrentPersonProvider currentPersonProvider;

    public Long createMovie(CreateMovieRequest request) {
        Person person = currentPersonProvider.getRequired();
        Movie movie = Movie.create(
                request.title(), request.runtime(), request.releaseYear(),
                request.movieUrl(), request.thumbnailUrl(), request.ageRating()
        );
        MoviePerson moviePerson = movie.addMoviePerson(
                person, request.role(), request.castType(), request.characterName()
        );
        Integer max = moviePersonRepository.findMaxSortOrderByPersonId(person.getId());
        moviePerson.changeSortOrder(max == null ? 0 : max + 1);
        movieGenreNormalizer.attachGenre(movie, request.genres());
        return movieRepository.save(movie).getId();
    }
}
