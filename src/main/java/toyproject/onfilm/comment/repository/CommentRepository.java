package toyproject.onfilm.comment.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import toyproject.onfilm.comment.entity.Comment;

import java.util.List;

public interface CommentRepository extends MongoRepository<Comment, String> {
    List<Comment> findByMovieId(Long movieId);    //특정 영화의 댓글 목록 조회
}
