package toyproject.onfilm.movie.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import toyproject.onfilm.movie.dto.*;
import toyproject.onfilm.movie.entity.Movie;
import toyproject.onfilm.movie.service.MovieService;

import java.util.Base64;
import java.util.List;



@Tag(name = "Movie API", description = "영화 관련 API")
@RequiredArgsConstructor
@RequestMapping("/movies")
@RestController
public class MovieController {

    private final MovieService movieService;

    /**
     * 웹 메인 화면에 사용할 영화 썸네일
     */
    @GetMapping()
    public ResponseEntity<List<MovieThumbnailResponse>> findAllMovies() {
        List<MovieThumbnailResponse> movies = movieService.findAllWithThumbnailUrl();
        return ResponseEntity.ok().body(movies);
    }

    /**
     * 최신 등록 기준
     * 썸네일, 제목
     */
    @GetMapping("/releaseDate")
    public ResponseEntity<Page<MovieThumbnailResponse>> findAllMoviesByReleaseDate(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Page<MovieThumbnailResponse> movies = movieService.findAllByReleaseDate(page, size);
        return ResponseEntity.ok().body(movies);
    }


    @PostMapping()
    public ResponseEntity<Long> createMovie(@Valid @RequestBody CreateMovieRequest request) {
        Long movieId = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(movieId);
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteMovie(@PathVariable Long movieId) {
        movieService.deleteMovie(movieId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 페치 조인을 두 번 나눠서 영화를 조회
     */
    @GetMapping("/v1/{id}")
    public ResponseEntity<MovieResponse> findMovieByIdV1(@PathVariable Long id) {
        MovieResponse findMovie = movieService.findMovieByIdV1(id);
        return ResponseEntity.ok().body(findMovie);
    }

    /**
     * 리포지토리에서 직접 DTO로 받아서 영화를 조회
     */
//    @GetMapping("/v2/{id}")
//    public ResponseEntity<MovieDetailResponse> findMovieByIdV2(@PathVariable Long id) {
//        MovieDetailResponse findMovie = movieService.findMovieByIdV2(id);
//        return ResponseEntity.ok().body(findMovie);
//    }

    /**
     * 배치 처리를 이용한 영화 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<MovieResponse> findMovieByIdV3(@PathVariable Long id) {
        MovieResponse findMovie = movieService.findMovieByIdV3(id);
        return ResponseEntity.ok().body(findMovie);
    }

    /**
     * 영화 카드형태 조회
     */
    @GetMapping("/card/{id}")
    public ResponseEntity<MovieCardResponse> findMovieCardById(@PathVariable Long id) {
        MovieCardResponse findMovie = movieService.findMovieCardById(id);
        return ResponseEntity.ok().body(findMovie);
    }

    /**
     * 영화 상세 조회
     */
    @GetMapping("/detail/{id}")
    public ResponseEntity<MovieDetailResponse> findMovieDetailById(@PathVariable Long id) {
        MovieDetailResponse findMovie = movieService.findMovieDetailById(id);
        return ResponseEntity.ok().body(findMovie);
    }

    /**
     * 영화 추가정보 조회
     */
    @GetMapping("/extra/{id}")
    public ResponseEntity<MovieExtraInfoResponse> findMovieExtraInfoById(@PathVariable Long id) {
        MovieExtraInfoResponse findMovie = movieService.findMovieExtraInfoById(id);
        return ResponseEntity.ok().body(findMovie);
    }

    /**
     * 영화 정보 수정
     */
    @PutMapping("/{movieId}")
    public ResponseEntity<Void> updateMovie(@PathVariable Long movieId, @RequestBody @Valid UpdateMovieRequest request) {
        movieService.updateMovie(movieId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 영화 title, movieUrl 조회
     */
    @GetMapping("/watch/{movieId}")
    public ResponseEntity<MovieWatchResponse> watchMovie(@PathVariable Long movieId) {
        MovieWatchResponse movieWatchResponse = movieService.watchMovie(movieId);
        return ResponseEntity.ok().body(movieWatchResponse);
    }

}

