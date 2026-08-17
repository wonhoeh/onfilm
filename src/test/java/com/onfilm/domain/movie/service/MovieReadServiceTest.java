package com.onfilm.domain.movie.service;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.MovieGenreResponse;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.repository.MovieGenreRepository;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.PersonRepository;
import com.onfilm.domain.movie.repository.TrailerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MovieReadServiceTest {

    @Mock
    private MoviePersonRepository moviePersonRepository;

    @Mock
    private MovieGenreRepository movieGenreRepository;

    @Mock
    private TrailerRepository trailerRepository;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private MovieReadService movieReadService;

    @Test
    void getFilmography_returnsStructuredStandardAndCustomGenres() {
        Person person = mock(Person.class);
        Movie movie = movie();
        MoviePerson moviePerson = moviePerson(movie);
        MovieGenre standardMovieGenre = standardMovieGenre(movie);
        MovieGenre customMovieGenre = customMovieGenre(movie);

        given(personRepository.findByPublicId("person-public-id"))
                .willReturn(Optional.of(person));
        given(person.getId()).willReturn(1L);
        given(moviePersonRepository.findFilmographyByPersonId(1L))
                .willReturn(List.of(moviePerson));
        given(movieGenreRepository.findAllByMovieIds(List.of(10L)))
                .willReturn(List.of(standardMovieGenre, customMovieGenre));
        given(trailerRepository.findAllByMovieIds(List.of(10L)))
                .willReturn(List.of());

        List<MovieCardResponse> result = movieReadService
                .getFilmographyByPublicId("person-public-id");

        assertThat(result).singleElement().satisfies(movieResponse ->
                assertThat(movieResponse.genres()).containsExactly(
                        new MovieGenreResponse(
                                100L,
                                2L,
                                "Action",
                                false
                        ),
                        new MovieGenreResponse(
                                101L,
                                null,
                                "화려한 액션",
                                true
                        )
                )
        );
    }

    private static Movie movie() {
        Movie movie = mock(Movie.class);
        given(movie.getId()).willReturn(10L);
        given(movie.getTitle()).willReturn("Test Movie");
        given(movie.getRuntime()).willReturn(120);
        given(movie.getReleaseYear()).willReturn(2020);
        given(movie.getAgeRating()).willReturn(AgeRating.ALL);
        given(movie.getMovieUrl()).willReturn("movie-key");
        return movie;
    }

    private static MoviePerson moviePerson(Movie movie) {
        MoviePerson moviePerson = mock(MoviePerson.class);
        given(moviePerson.getMovie()).willReturn(movie);
        return moviePerson;
    }

    private static MovieGenre standardMovieGenre(Movie movie) {
        Genre genre = mock(Genre.class);
        MovieGenre movieGenre = mock(MovieGenre.class);
        given(genre.getId()).willReturn(2L);
        given(movieGenre.getId()).willReturn(100L);
        given(movieGenre.getMovie()).willReturn(movie);
        given(movieGenre.getGenre()).willReturn(genre);
        given(movieGenre.getRawText()).willReturn("Action");
        return movieGenre;
    }

    private static MovieGenre customMovieGenre(Movie movie) {
        MovieGenre movieGenre = mock(MovieGenre.class);
        given(movieGenre.getId()).willReturn(101L);
        given(movieGenre.getMovie()).willReturn(movie);
        given(movieGenre.getRawText()).willReturn("화려한 액션");
        return movieGenre;
    }
}
