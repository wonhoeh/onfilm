package toyproject.onfilm.like.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import toyproject.onfilm.like.entity.CommentLike;

import java.util.Optional;

public interface CommentLikeRepository extends MongoRepository<CommentLike, String> {
    Optional<CommentLike> findByCommentIdAndUserId(String commentId,String userId);
    long countByCommentId(String commentId);
    void deleteByCommentIdAndUserId(String commentId, String userId);
}
