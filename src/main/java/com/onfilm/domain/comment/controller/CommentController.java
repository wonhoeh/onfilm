package com.onfilm.domain.comment.controller;

import com.onfilm.domain.comment.dto.CommentRequest;
import com.onfilm.domain.comment.entity.Comment;
import com.onfilm.domain.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RequiredArgsConstructor
@RequestMapping("/comments")
@RestController
public class CommentController {

    private final CommentService commentService;

    //댓글 추가
    @PostMapping()
    public ResponseEntity<Comment> addComment(@RequestBody CommentRequest request) {
        Comment comment = commentService.addComment(request);
        return ResponseEntity.ok().body(comment);
    }

    //영화의 ID로 댓글 조회
    @GetMapping("/{movieId}")
    public ResponseEntity<List<Comment>> getCommentsByMovie(@PathVariable Long movieId) {
        List<Comment> comments = commentService.getCommentsByMovie(movieId);
        return ResponseEntity.ok().body(comments);
    }
}
