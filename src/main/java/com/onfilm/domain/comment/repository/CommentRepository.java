package com.onfilm.domain.comment.repository;

import com.onfilm.domain.comment.entity.Comment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends MongoRepository<Comment, String> {
    List<Comment> findAllByMovieId(Long movieId);    //특정 영화의 댓글 목록 조회
    Optional<Comment> findById(String id);
}
