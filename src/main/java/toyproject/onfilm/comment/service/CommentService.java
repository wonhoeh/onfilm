package toyproject.onfilm.comment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import toyproject.onfilm.comment.entity.Comment;
import toyproject.onfilm.comment.repository.CommentRepository;
import toyproject.onfilm.exception.MovieNotFoundException;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final MovieRepository movieRepository;

    //댓글 추가
    public Comment addComment(Long movieId, String username, String content) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));

        Comment comment = new Comment(movie.getId(), username, content);
        commentRepository.save(comment);
        movie.addComment(comment.getId());
        return comment;
    }

    //특정 영화의 댓글 조회
    public List<Comment> getCommentsByMovie(Long movieId) {
        return commentRepository.findByMovieId(movieId);
    }
}
