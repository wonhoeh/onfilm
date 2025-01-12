package toyproject.onfilm.movie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import toyproject.onfilm.movie.entity.Movie;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    @Query("select m from Movie m join fetch m.movieTrailers where m.id = :id")
    Movie findMovieWithTrailers(@Param("id") Long id);

    @Query("select m from Movie m join fetch m.movieActors where m.id = :id")
    Movie findMovieWithActors(@Param("id") Long id);

    @Query("select m from Movie m Join FETCH m.movieTrailers")
    List<Movie> findAllWithTrailers();

//    @Query("select new toyproject.onfilm.movie.dto.MovieDetailsDto(m.id, m.title, m.runtime, m.ageRating, t.trailUrl, t.thumbnailUrl, a.actor.profile.name, a.actor.profile.age, a.actor.profile.sns, a.actorsRole) " +
//            "from Movie m " +
//            "left join m.movieTrailers t " +
//            "left join m.movieActors a " +
//            "where m.id = :id")
//    List<MovieDetailsDto> findMovieDetails(@Param("id") Long id);

    //    @Query("select distinct new toyproject.onfilm.movie.dto.MovieDetailsDto(m.id, m.title, m.runtime, m.ageRating, t.trailUrl, a.actor.profile.name, a.actorsRole) " +
//            "from Movie m " +
//            "JOIN m.movieTrailers t " +
//            "JOIN m.movieActors a")
//    Movie findMovieWithTrailersAndActors(@Param("id") Long id);
}
