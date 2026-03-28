package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByName(String name);
    Optional<Person> findByPublicId(String publicId);

    @Query("""
        SELECT DISTINCT p FROM Person p
        LEFT JOIN FETCH p.storyboardProjects sp
        LEFT JOIN FETCH sp.scenes
        WHERE p.publicId = :publicId
    """)
    Optional<Person> findByPublicIdWithStoryboards(@Param("publicId") String publicId);

    @Query("select p.profileImageUrl from Person p where p.id = :personId")
    Optional<String> findProfileImageKeyById(@Param("personId") Long personId);

    @Query("select p.filmographyFileKey from Person p where p.id = :personId")
    Optional<String> findFilmographyKeyById(@Param("personId") Long personId);
}
