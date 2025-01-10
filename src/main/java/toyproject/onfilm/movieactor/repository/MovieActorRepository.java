package toyproject.onfilm.movieactor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.movieactor.entity.MovieActor;

public interface MovieActorRepository extends JpaRepository<MovieActor, Long> {
}
