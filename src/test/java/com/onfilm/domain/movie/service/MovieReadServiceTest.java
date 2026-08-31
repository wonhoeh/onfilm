package com.onfilm.domain.movie.service;

import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.MovieRoleResponse;
import com.onfilm.domain.movie.entity.*;
import com.onfilm.domain.movie.repository.*;
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
class FilmographyQueryServiceTest {
    @Mock MoviePersonRepository moviePersonRepository;
    @Mock MovieGenreRepository movieGenreRepository;
    @Mock TrailerRepository trailerRepository;
    @Mock PersonRepository personRepository;
    @Mock StorageService storageService;
    @Mock CurrentPersonProvider currentPersonProvider;
    @InjectMocks FilmographyQueryService queryService;

    @Test
    void queryHidesPrivateItemFromVisitor() {
        Person person = mock(Person.class);
        Movie movie = mock(Movie.class);
        MoviePerson credit = mock(MoviePerson.class);
        given(personRepository.findByPublicId("person-id")).willReturn(Optional.of(person));
        given(person.getId()).willReturn(1L);
        given(currentPersonProvider.isCurrentPerson(1L)).willReturn(false);
        given(moviePersonRepository.findFilmographyByPersonId(1L)).willReturn(List.of(credit));
        given(credit.getMovie()).willReturn(movie);
        given(movie.getId()).willReturn(10L);
        given(credit.isPrivate()).willReturn(true);
        given(movieGenreRepository.findAllByMovieIds(List.of(10L))).willReturn(List.of());
        given(trailerRepository.findAllByMovieIds(List.of(10L))).willReturn(List.of());

        List<MovieCardResponse> result = queryService.findVisibleFilmography("person-id");

        assertThat(result).isEmpty();
    }

    @Test
    void queryReturnsAllRolesForOneFilmographyItem() {
        Person person = mock(Person.class);
        Movie movie = mock(Movie.class);
        MoviePerson participation = mock(MoviePerson.class);
        MoviePersonRole actor = mock(MoviePersonRole.class);
        MoviePersonRole writer = mock(MoviePersonRole.class);
        given(personRepository.findByPublicId("person-id")).willReturn(Optional.of(person));
        given(person.getId()).willReturn(1L);
        given(currentPersonProvider.isCurrentPerson(1L)).willReturn(true);
        given(moviePersonRepository.findFilmographyByPersonId(1L))
                .willReturn(List.of(participation));
        given(participation.getMovie()).willReturn(movie);
        given(participation.getRoles()).willReturn(List.of(actor, writer));
        given(movie.getId()).willReturn(10L);
        given(actor.getRole()).willReturn(PersonRole.ACTOR);
        given(actor.getCastType()).willReturn(CastType.LEAD);
        given(actor.getCharacterName()).willReturn("주인공");
        given(writer.getRole()).willReturn(PersonRole.WRITER);
        given(movieGenreRepository.findAllByMovieIds(List.of(10L))).willReturn(List.of());
        given(trailerRepository.findAllByMovieIds(List.of(10L))).willReturn(List.of());

        List<MovieCardResponse> result = queryService.findVisibleFilmography("person-id");

        assertThat(result).singleElement().satisfies(response ->
                assertThat(response.roles())
                        .extracting(MovieRoleResponse::role)
                        .containsExactly(PersonRole.ACTOR, PersonRole.WRITER));
    }
}
