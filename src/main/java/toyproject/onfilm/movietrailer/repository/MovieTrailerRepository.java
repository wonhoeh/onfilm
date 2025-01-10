package toyproject.onfilm.movietrailer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;

public interface MovieTrailerRepository extends JpaRepository<MovieTrailer, Long> {
}
