package com.onfilm.domain.comment.service;

import com.onfilm.domain.comment.dto.CommentRequest;
import com.onfilm.domain.comment.entity.Comment;
import com.onfilm.domain.comment.repository.CommentRepository;
import com.onfilm.domain.global.error.exception.MovieNotFoundException;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final MovieRepository movieRepository;

    //댓글 작성
    public Comment addComment(CommentRequest request) {
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));

        Comment comment = new Comment(movie.getId(), request.getUsername(), request.getContent());
        commentRepository.save(comment);
        movie.addComment(comment.getId());
        return comment;
    }

    //영화의 Id로 모든 댓글 조회
    public List<Comment> getCommentsByMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));
        return commentRepository.findAllByMovieId(movie.getId());
    }
}
