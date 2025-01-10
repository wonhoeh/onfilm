package toyproject.onfilm.comment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.comment.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}
