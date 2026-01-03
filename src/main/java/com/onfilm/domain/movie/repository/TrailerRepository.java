package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Trailer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrailerRepository extends JpaRepository<Trailer, Long> {
    @Query("""
        select t
        from Trailer t
        where t.movie.id in :movieIds
    """)
    List<Trailer> findAllByMovieIds(@Param("movieIds") List<Long> movieIds);
}
