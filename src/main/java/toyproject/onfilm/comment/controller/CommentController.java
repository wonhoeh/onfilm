package toyproject.onfilm.comment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import toyproject.onfilm.comment.dto.CommentRequest;
import toyproject.onfilm.comment.entity.Comment;
import toyproject.onfilm.comment.service.CommentService;

import java.util.List;


@RequiredArgsConstructor
@RequestMapping("/comments")
@RestController
public class CommentController {

    private final CommentService commentService;

    //댓글 추가
    @PostMapping()
    public ResponseEntity<Comment> addComment(@RequestBody CommentRequest request) {
        Comment comment = commentService.addComment(request.getMovieId(), request.getUsername(), request.getContent());
        return ResponseEntity.ok().body(comment);
    }

    //특정 영화의 댓글 조회
    @GetMapping("/{movieId}")
    public ResponseEntity<List<Comment>> getCommentsByMovie(@PathVariable Long movieId) {
        List<Comment> comments = commentService.getCommentsByMovie(movieId);
        return ResponseEntity.ok().body(comments);
    }
}
