package toyproject.onfilm.like.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import toyproject.onfilm.like.entity.Like;

import java.util.Optional;

public interface LikeRepository extends MongoRepository<Like, String> {
    Optional<Like> findByMovieIdAndClientId(Long movieId, String clientId);
    long countByMovieId(Long movieId);
    void deleteByMovieIdAndClientId(Long movieId, String clientId);
}
