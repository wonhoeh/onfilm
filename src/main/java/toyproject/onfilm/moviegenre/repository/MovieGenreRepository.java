package toyproject.onfilm.moviegenre.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.domain.moviegenre.MovieGenre;
import toyproject.onfilm.moviegenre.dto.MovieGenre;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, Long> {
}
