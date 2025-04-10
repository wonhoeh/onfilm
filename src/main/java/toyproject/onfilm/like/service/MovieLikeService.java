package toyproject.onfilm.like.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import toyproject.onfilm.exception.MovieNotFoundException;
import toyproject.onfilm.like.entity.MovieLike;
import toyproject.onfilm.like.repository.MovieLikeRepository;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class MovieLikeService {

    private final MovieLikeRepository movieLikeRepository;
    private final MovieRepository movieRepository;

    //좋아요 추가
    public boolean addLike(Long movieId, String clientId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));
        Optional<MovieLike> existingLike = movieLikeRepository.findByMovieIdAndClientId(movie.getId(), clientId);
        if (existingLike.isEmpty()) {
            MovieLike like = MovieLike.create(movie.getId(), clientId);
            movieLikeRepository.save(like);
            movie.addLike(like.getId());
            return true;    //좋아요 성공
        }
        return false;       //이미 좋아요를 누른 경우
    }

    //좋아요 취소
    public boolean removeLike(Long movieId, String clientId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다"));
        Optional<MovieLike> existingLike = movieLikeRepository.findByMovieIdAndClientId(movie.getId(), clientId);
        if (existingLike.isPresent()) {
            movieLikeRepository.deleteByMovieIdAndClientId(movie.getId(), clientId);
            movie.removeLike(existingLike.get().getId());
            return true;    //좋아요 취소 성공
        }
        return false;       //좋아요가 없는 경우
    }

    //특정 영화의 좋아요 개수 조회
    public long getLikeCount(Long movieId) {
        return movieLikeRepository.countByMovieId(movieId);
    }
}
