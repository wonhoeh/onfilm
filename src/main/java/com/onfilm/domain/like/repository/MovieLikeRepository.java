package com.onfilm.domain.like.repository;

import com.onfilm.domain.like.entity.MovieLike;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MovieLikeRepository extends MongoRepository<MovieLike, String> {
    Optional<MovieLike> findByMovieIdAndClientId(Long movieId, String clientId);
    long countByMovieId(Long movieId);
    void deleteByMovieIdAndClientId(Long movieId, String clientId);
}
