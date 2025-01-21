package toyproject.onfilm.like.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import toyproject.onfilm.comment.entity.Comment;
import toyproject.onfilm.comment.repository.CommentRepository;
import toyproject.onfilm.exception.CommentNotFoundException;
import toyproject.onfilm.like.entity.CommentLike;
import toyproject.onfilm.like.repository.CommentLikeRepository;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CommentLikeService {

    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;

    //좋아요 추가
    public boolean addLike(String commentId, String userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("댓글을 찾을 수 없습니다"));
        Optional<CommentLike> existingLike = commentLikeRepository.findByCommentIdAndUserId(comment.getId(), userId);
        if (existingLike.isEmpty()) {
            CommentLike commentLike = CommentLike.create(comment.getId(), userId);
            commentLikeRepository.save(commentLike);
            comment.addLike(commentLike.getId());
            commentRepository.save(comment);
            return true;    //좋아요 성공
        }
        return false;       //이미 좋아요를 누른 경우
    }

    //좋아요 취소
    public boolean removeLike(String commentId, String userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("댓글을 찾을 수 없습니다"));
        Optional<CommentLike> existingLike = commentLikeRepository.findByCommentIdAndUserId(comment.getId(), userId);
        if (existingLike.isPresent()) {
            commentLikeRepository.deleteByCommentIdAndUserId(comment.getId(), userId);
            comment.removeLike(existingLike.get().getId());
            return true;    //좋아요 취소 성공
        }
        return false;       //좋아요가 없는 경우
    }

    //특정 영화의 좋아요 개수 조회
    public long getLikeCount(String commentId) {
        return commentLikeRepository.countByCommentId(commentId);
    }
}
