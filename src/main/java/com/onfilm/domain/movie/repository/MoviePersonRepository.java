package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MoviePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MoviePersonRepository extends JpaRepository<MoviePerson, Long> {
    @Query("select mp from MoviePerson mp join fetch mp.movie where mp.person.id = :personId")
    List<MoviePerson> findFilmography(@Param("personId") Long personId);
}
