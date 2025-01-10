package toyproject.onfilm.genre.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.genre.entity.Genre;

public interface GenreRepository extends JpaRepository<Genre, Long> {
}
