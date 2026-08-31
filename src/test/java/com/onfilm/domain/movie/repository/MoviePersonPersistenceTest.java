package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class MoviePersonPersistenceTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MoviePersonRepository moviePersonRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void participationAndMultipleRoles_arePersistedAndFetchedTogether() {
        Person person = personRepository.saveAndFlush(createPerson());
        Movie movie = createMovie();
        movie.addMoviePerson(
                person,
                List.of(
                        role(PersonRole.ACTOR, CastType.LEAD, "주인공"),
                        role(PersonRole.DIRECTOR, null, null),
                        role(PersonRole.WRITER, null, null)
                )
        );
        movieRepository.saveAndFlush(movie);
        entityManager.clear();

        assertThat(moviePersonRepository.findFilmographyByPersonId(person.getId()))
                .singleElement()
                .satisfies(moviePerson -> {
                    assertThat(moviePerson.getMovie().getId()).isEqualTo(movie.getId());
                    assertThat(moviePerson.getRoles())
                            .extracting(MoviePersonRole::getRole)
                            .containsExactly(PersonRole.ACTOR, PersonRole.DIRECTOR, PersonRole.WRITER);
                });
    }

    @Test
    void replaceRoles_updatesExistingRoleAndDeletesRemovedRoleAsOrphan() {
        Person person = personRepository.saveAndFlush(createPerson());
        Movie movie = createMovie();
        movie.addMoviePerson(
                person,
                List.of(
                        role(PersonRole.ACTOR, CastType.SUPPORTING, "친구"),
                        role(PersonRole.DIRECTOR, null, null)
                )
        );
        movieRepository.saveAndFlush(movie);
        entityManager.clear();

        MoviePerson moviePerson = moviePersonRepository
                .findFilmographyByPersonId(person.getId())
                .get(0);
        Long actorRoleId = moviePerson.getRoles().get(0).getId();
        moviePerson.replaceRoles(List.of(
                role(PersonRole.ACTOR, CastType.LEAD, "주인공"),
                role(PersonRole.WRITER, null, null)
        ));
        entityManager.flush();
        entityManager.clear();

        MoviePerson reloaded = moviePersonRepository
                .findFilmographyByPersonId(person.getId())
                .get(0);
        assertThat(reloaded.getRoles())
                .extracting(MoviePersonRole::getRole)
                .containsExactly(PersonRole.ACTOR, PersonRole.WRITER);
        assertThat(reloaded.getRoles().get(0).getId()).isEqualTo(actorRoleId);
        assertThat(reloaded.getRoles().get(0).getCastType()).isEqualTo(CastType.LEAD);
        assertThat(reloaded.getRoles().get(0).getCharacterName()).isEqualTo("주인공");
        assertThat(roleCount()).isEqualTo(2);
    }

    @Test
    void database_rejectsDuplicateParticipation() {
        Person person = personRepository.saveAndFlush(createPerson());
        Movie movie = createMovie();
        movie.addMoviePerson(person, PersonRole.DIRECTOR, null, null);
        movieRepository.saveAndFlush(movie);

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        insert into movie_person (movie_id, person_id, sort_order, is_private)
                        values (:movieId, :personId, 1, false)
                        """)
                .setParameter("movieId", movie.getId())
                .setParameter("personId", person.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void database_rejectsDuplicateRoleInParticipation() {
        Person person = personRepository.saveAndFlush(createPerson());
        Movie movie = createMovie();
        MoviePerson moviePerson = movie.addMoviePerson(
                person,
                PersonRole.DIRECTOR,
                null,
                null
        );
        movieRepository.saveAndFlush(movie);

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        insert into movie_person_role
                            (movie_person_id, role, cast_type, character_name, sort_order)
                        values (:moviePersonId, 'DIRECTOR', null, null, 1)
                        """)
                .setParameter("moviePersonId", moviePerson.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void database_rejectsActorFieldsForNonActorRole() {
        Person person = personRepository.saveAndFlush(createPerson());
        Movie movie = createMovie();
        MoviePerson moviePerson = movie.addMoviePerson(
                person,
                PersonRole.DIRECTOR,
                null,
                null
        );
        movieRepository.saveAndFlush(movie);

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        insert into movie_person_role
                            (movie_person_id, role, cast_type, character_name, sort_order)
                        values (:moviePersonId, 'WRITER', 'LEAD', null, 1)
                        """)
                .setParameter("moviePersonId", moviePerson.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    private long roleCount() {
        return ((Number) entityManager.createNativeQuery(
                "select count(*) from movie_person_role"
        ).getSingleResult()).longValue();
    }

    private static MoviePerson.RoleRegistration role(
            PersonRole role,
            CastType castType,
            String characterName
    ) {
        return new MoviePerson.RoleRegistration(role, castType, characterName);
    }

    private static Movie createMovie() {
        return Movie.create(
                "영화",
                120,
                2020,
                "movie-key",
                null,
                AgeRating.ALL
        );
    }

    private static Person createPerson() {
        return Person.create(
                "테스트 인물",
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
