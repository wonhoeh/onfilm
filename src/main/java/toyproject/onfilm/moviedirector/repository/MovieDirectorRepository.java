package toyproject.onfilm.moviedirector.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.moviedirector.entity.MovieDirector;

public interface MovieDirectorRepository extends JpaRepository<MovieDirector, Long> {
}
