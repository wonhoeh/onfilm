package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MoviePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MoviePersonRepository extends JpaRepository<MoviePerson, Long> {
    @Query("""
        select mp
        from MoviePerson mp
        join fetch mp.movie m
        join mp.person p
        where p.name = :name
        order by
            case when m.releaseYear is null then 1 else 0 end,
            m.releaseYear desc,
            mp.id desc
    """)
    List<MoviePerson> findFilmographyByPersonName(@Param("name") String name);

}