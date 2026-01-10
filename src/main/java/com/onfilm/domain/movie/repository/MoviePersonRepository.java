package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MoviePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MoviePersonRepository extends JpaRepository<MoviePerson, Long> {
    @Query("""
            select mp from MoviePerson mp
            join fetch mp.movie m
            where mp.person.id = :personId
            order by coalesce(mp.sortOrder, 999999), mp.id
            """)
    List<MoviePerson> findFilmographyByPersonId(@Param("personId") Long personId);

    @Query("select coalesce(max(mp.sortOrder), 0) from MoviePerson mp where mp.person.id = :personId")
    Integer findMaxSortOrderByPersonId(@Param("personId") Long personId);

    @Query("select mp from MoviePerson mp where mp.person.id = :personId and mp.movie.id = :movieId")
    MoviePerson findByPersonIdAndMovieId(@Param("personId") Long personId, @Param("movieId") Long movieId);

}
