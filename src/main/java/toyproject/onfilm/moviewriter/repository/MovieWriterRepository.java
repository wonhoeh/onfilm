package toyproject.onfilm.moviewriter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.moviewriter.entity.MovieWriter;

public interface MovieWriterRepository extends JpaRepository<MovieWriter, Long> {
}
