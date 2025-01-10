package toyproject.onfilm.movie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import toyproject.onfilm.movie.entity.Movie;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    @Query("SELECT m FROM Movie m JOIN FETCH m.movieTrailers WHERE m.id = :id")
    Movie findMovieWithTrailers(@Param("id") Long id);
}
