package com.onfilm.domain.movie.repository;

import com.onfilm.domain.genre.entity.Genre;
import com.onfilm.domain.genre.repository.GenreRepository;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.CastType;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.entity.MovieGenre;
import com.onfilm.domain.movie.entity.MoviePerson;
import com.onfilm.domain.movie.entity.MoviePersonRole;
import com.onfilm.domain.movie.entity.Person;
import com.onfilm.domain.movie.entity.PersonRole;
import com.onfilm.domain.movie.entity.Trailer;
import com.onfilm.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MovieRepositoryMySqlIntegrationTest extends MySqlContainerSupport {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MoviePersonRepository moviePersonRepository;

    @Autowired
    private MovieGenreRepository movieGenreRepository;

    @Autowired
    private TrailerRepository trailerRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void filmographyQueryFetchesMultipleRolesInDisplayOrder() {
        Person person = personRepository.saveAndFlush(createPerson("참여자"));
        Genre action = genreRepository.findById(1L).orElseThrow();

        Movie first = createMovie("먼저 표시할 작품", "movie/first/index.m3u8");
        MoviePerson firstParticipation = first.addMoviePerson(
                person,
                List.of(
                        role(PersonRole.ACTOR, CastType.LEAD, "주인공"),
                        role(PersonRole.WRITER, null, null)
                )
        );
        firstParticipation.changeSortOrder(0);
        first.addStandardGenre(action);
        first.addTrailer("movie/first/trailer/first/index.m3u8");

        Movie second = createMovie("나중에 표시할 작품", "movie/second/index.m3u8");
        MoviePerson secondParticipation = second.addMoviePerson(
                person,
                PersonRole.DIRECTOR,
                null,
                null
        );
        secondParticipation.changeSortOrder(1);
        second.addCustomGenre("독립 영화");
        second.addTrailer("movie/second/trailer/first/index.m3u8");

        movieRepository.saveAllAndFlush(List.of(second, first));
        entityManager.clear();

        List<MoviePerson> filmography = moviePersonRepository
                .findFilmographyByPersonId(person.getId());

        assertThat(filmography)
                .extracting(moviePerson -> moviePerson.getMovie().getTitle())
                .containsExactly("먼저 표시할 작품", "나중에 표시할 작품");
        assertThat(filmography.get(0).getRoles())
                .extracting(MoviePersonRole::getRole)
                .containsExactly(PersonRole.ACTOR, PersonRole.WRITER);
        assertThat(filmography.get(0).getRoles().get(0).getCharacterName())
                .isEqualTo("주인공");

        assertThat(movieGenreRepository.findAllByMovieIds(
                List.of(first.getId(), second.getId())
        )).extracting(MovieGenre::getRawText)
                .containsExactlyInAnyOrder("액션", "독립 영화");
        assertThat(trailerRepository.findAllByMovieIds(
                List.of(first.getId(), second.getId())
        )).extracting(Trailer::getStorageKey)
                .containsExactly(
                        "movie/second/trailer/first/index.m3u8",
                        "movie/first/trailer/first/index.m3u8"
                );
    }

    @Test
    void removingOwnedChildrenDeletesOrphansAndCompactsOrderColumns() {
        Movie movie = createMovie("자식 삭제 검증", "movie/orphan/index.m3u8");
        Trailer first = movie.addTrailer("movie/orphan/trailer/first/index.m3u8");
        Trailer removedTrailer = movie.addTrailer("movie/orphan/trailer/second/index.m3u8");
        Trailer third = movie.addTrailer("movie/orphan/trailer/third/index.m3u8");
        MovieGenre removedGenre = movie.addCustomGenre("삭제할 장르");
        movie.addCustomGenre("유지할 장르");
        movieRepository.saveAndFlush(movie);

        Long movieId = movie.getId();
        Long removedTrailerId = removedTrailer.getId();
        Long removedGenreId = removedGenre.getId();
        movie.removeTrailer(removedTrailer);
        movie.removeMovieGenre(removedGenre);
        movieRepository.flush();
        entityManager.clear();

        Movie reloaded = movieRepository.findById(movieId).orElseThrow();
        assertThat(reloaded.getTrailers())
                .extracting(Trailer::getStorageKey)
                .containsExactly(first.getStorageKey(), third.getStorageKey());
        assertThat(storedTrailerSortOrders(movieId)).containsExactly(0, 1);
        assertThat(trailerRepository.findById(removedTrailerId)).isEmpty();
        assertThat(movieGenreRepository.findById(removedGenreId)).isEmpty();
    }

    @Test
    void deletingMovieCascadesToOwnedRelationsButKeepsParticipant() {
        Person person = personRepository.saveAndFlush(createPerson("삭제 후 유지할 인물"));
        Movie movie = createMovie("삭제할 작품", "movie/delete/index.m3u8");
        MoviePerson participation = movie.addMoviePerson(
                person,
                PersonRole.ACTOR,
                CastType.SUPPORTING,
                "조연"
        );
        Trailer trailer = movie.addTrailer("movie/delete/trailer/index.m3u8");
        MovieGenre genre = movie.addCustomGenre("삭제 대상 장르");
        movieRepository.saveAndFlush(movie);

        Long movieId = movie.getId();
        Long personId = person.getId();
        Long participationId = participation.getId();
        Long trailerId = trailer.getId();
        Long genreId = genre.getId();

        movieRepository.deleteById(movieId);
        movieRepository.flush();
        entityManager.clear();

        assertThat(movieRepository.findById(movieId)).isEmpty();
        assertThat(moviePersonRepository.findById(participationId)).isEmpty();
        assertThat(trailerRepository.findById(trailerId)).isEmpty();
        assertThat(movieGenreRepository.findById(genreId)).isEmpty();
        assertThat(personRepository.findById(personId)).isPresent();
    }

    private List<Integer> storedTrailerSortOrders(Long movieId) {
        List<?> values = entityManager.createNativeQuery("""
                        select sort_order
                        from trailer
                        where movie_id = :movieId
                        order by sort_order
                        """)
                .setParameter("movieId", movieId)
                .getResultList();
        return values.stream()
                .map(value -> ((Number) value).intValue())
                .toList();
    }

    private static MoviePerson.RoleRegistration role(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        return new MoviePerson.RoleRegistration(role, castType, characterName);
    }

    private static Movie createMovie(String title, String movieKey) {
        return Movie.create(title, 120, 2026, movieKey, null, AgeRating.ALL);
    }

    private static Person createPerson(String name) {
        return Person.create(name, null, null, null, null, List.of(), List.of());
    }
}
