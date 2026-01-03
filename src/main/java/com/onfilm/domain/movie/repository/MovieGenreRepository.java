package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.MovieGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, Long> {
    @Query("""
        select mg
        from MovieGenre mg
        where mg.movie.id in :movieIds
    """)
    List<MovieGenre> findAllByMovieIds(@Param("movieIds") List<Long> movieIds);
}
