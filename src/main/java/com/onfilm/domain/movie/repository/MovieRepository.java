package com.onfilm.domain.movie.repository;

import com.onfilm.domain.movie.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    @Query("select m from Movie m left join fetch m.movieTrailers where m.id = :id")
    Movie findMovieWithTrailers(@Param("id") Long id);

    @Query("select m from Movie m join fetch m.movieTrailers")
    List<Movie> findAllWithTrailers();

    @Query("SELECT m FROM Movie m ORDER BY m.releaseYear DESC")
    Page<Movie> findAllByOrderByReleaseDateDesc(Pageable pageable);

//    @Query("select new toyproject.onfilm.movie.dto.MovieDetailsDto(m.id, m.title, m.runtime, m.ageRating, t.trailUrl, t.thumbnailUrl, a.actor.profile.name, a.actor.profile.age, a.actor.profile.sns, a.actorRole) " +
//            "from Movie m " +
//            "left join m.movieTrailers t " +
//            "left join m.movieActors a " +
//            "where m.id = :id")
//    List<MovieDetailsDto> findMovieDetails(@Param("id") Long id);

    //    @Query("select distinct new toyproject.onfilm.movie.dto.MovieDetailsDto(m.id, m.title, m.runtime, m.ageRating, t.trailUrl, a.actor.profile.name, a.actorRole) " +
//            "from Movie m " +
//            "JOIN m.movieTrailers t " +
//            "JOIN m.movieActors a")
//    Movie findMovieWithTrailersAndActors(@Param("id") Long id);
}
