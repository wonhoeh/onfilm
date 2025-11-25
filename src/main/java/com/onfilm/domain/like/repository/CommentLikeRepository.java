package com.onfilm.domain.like.repository;

import com.onfilm.domain.like.entity.CommentLike;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CommentLikeRepository extends MongoRepository<CommentLike, String> {
    Optional<CommentLike> findByCommentIdAndUserId(String commentId, String userId);
    long countByCommentId(String commentId);
    void deleteByCommentIdAndUserId(String commentId, String userId);
}
