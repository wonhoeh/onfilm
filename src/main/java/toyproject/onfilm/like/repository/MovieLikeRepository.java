package toyproject.onfilm.like.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import toyproject.onfilm.like.entity.MovieLike;

import java.util.Optional;

public interface MovieLikeRepository extends MongoRepository<MovieLike, String> {
    Optional<MovieLike> findByMovieIdAndClientId(Long movieId, String clientId);
    long countByMovieId(Long movieId);
    void deleteByMovieIdAndClientId(Long movieId, String clientId);
}
