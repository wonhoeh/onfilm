package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MoviePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MoviePersonRepository extends JpaRepository<MoviePerson, Long>, MoviePersonQueryRepository {
    @Query("select mp from MoviePerson mp where mp.id = :id and mp.role = com.onfilm.domain.movie.entity.PersonRole.ACTOR")
    Optional<MoviePerson> findMovieActorById(@Param("id") Long id);

    @Query("select mp from MoviePerson mp where mp.id = :id and mp.role = com.onfilm.domain.movie.entity.PersonRole.ACTOR")
    Optional<MoviePerson> findMovieDirectorById(@Param("id") Long id);

    @Query("select mp from MoviePerson mp where mp.id = :id and mp.role = com.onfilm.domain.movie.entity.PersonRole.ACTOR")
    Optional<MoviePerson> findMovieWriterById(@Param("id") Long id);
}
