package toyproject.onfilm.movietrailer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.exception.MovieNotFoundException;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.repository.MovieRepository;
import toyproject.onfilm.movietrailer.dto.CreateTrailerRequest;
import toyproject.onfilm.movietrailer.entity.MovieTrailer;
import toyproject.onfilm.movietrailer.repository.MovieTrailerRepository;
import toyproject.onfilm.s3.service.FileService;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieTrailerService {

    private final MovieTrailerRepository movieTrailerRepository;
    private final MovieRepository movieRepository;
    private final FileService fileService;

    @Transactional
    public Long createTrailer(CreateTrailerRequest request) {
        // 1. Movie 찾기
        Long movieId = request.getMovieId();
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("영화를 찾을 수 없습니다: " + movieId));

        // 2. FileService를 이용해서 썸네일과 트레일러 업로드
        String thumbnailUrl = fileService.uploadFile(request.getThumbnail(), "thumbnail");
        String trailerUrl = fileService.uploadFile(request.getTrailer(), "trailer");

        // 3. MovieTrailer 저장
        MovieTrailer movieTrailer = MovieTrailer.builder()
                .thumbnailUrl(thumbnailUrl)
                .trailerUrl(trailerUrl)
                .build();

        MovieTrailer saved = movieTrailerRepository.save(movieTrailer);
        return saved.getId();
    }
}
