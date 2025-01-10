package toyproject.onfilm.movie.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.domain.movie.Movie;
import toyproject.onfilm.movie.entity.Movie;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
