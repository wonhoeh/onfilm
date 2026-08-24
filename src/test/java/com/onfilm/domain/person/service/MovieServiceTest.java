package com.onfilm.domain.person.service;

import com.onfilm.domain.movie.dto.CreateMovieRequest;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.MoviePersonRepository;
import com.onfilm.domain.movie.repository.MovieRepository;
import com.onfilm.domain.movie.service.CurrentPersonProvider;
import com.onfilm.domain.movie.service.MovieCommandService;
import com.onfilm.domain.movie.service.MovieGenreNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MovieCommandServiceTest {
    @Mock MovieRepository movieRepository;
    @Mock MoviePersonRepository moviePersonRepository;
    @Mock MovieGenreNormalizer movieGenreNormalizer;
    @Mock CurrentPersonProvider currentPersonProvider;
    @InjectMocks MovieCommandService movieCommandService;

    @Test
    void commandCreatesMovieAndAttachesCurrentPerson() throws Exception {
        Person person = mock(Person.class);
        given(person.getId()).willReturn(7L);
        given(currentPersonProvider.getRequired()).willReturn(person);
        given(moviePersonRepository.findMaxSortOrderByPersonId(7L)).willReturn(3);
        given(movieRepository.save(any(Movie.class))).willAnswer(invocation -> {
            Movie movie = invocation.getArgument(0);
            setId(movie, 10L);
            return movie;
        });
        CreateMovieRequest request = new CreateMovieRequest(
                "인셉션", 120, 2010, "movie/key", null, List.of(), AgeRating.ALL,
                PersonRole.ACTOR, CastType.LEAD, "코브"
        );

        Long result = movieCommandService.createMovie(request);

        assertThat(result).isEqualTo(10L);
        ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
        Mockito.verify(movieRepository).save(captor.capture());
        assertThat(captor.getValue().getMoviePeople()).singleElement().satisfies(credit -> {
            assertThat(credit.getPerson()).isSameAs(person);
            assertThat(credit.getSortOrder()).isEqualTo(4);
        });
    }

    private static void setId(Movie movie, Long id) throws Exception {
        Field field = Movie.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(movie, id);
    }
}
