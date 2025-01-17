package toyproject.onfilm.genre.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import toyproject.onfilm.genre.entity.Genre;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenreRepository extends MongoRepository<Genre, String> {
    Optional<Genre> findById(String id);
    Optional<Genre> findByName(String name);
}
