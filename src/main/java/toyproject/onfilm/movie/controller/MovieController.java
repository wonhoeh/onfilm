package toyproject.onfilm.movie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import toyproject.onfilm.movie.dto.CreateMovieRequest;
import toyproject.onfilm.movie.dto.MovieThumbnailResponse;
import toyproject.onfilm.movie.dto.MovieResponse;
import toyproject.onfilm.movie.service.MovieService;

import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

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
    @GetMapping("/v3/{id}")
    public ResponseEntity<MovieResponse> findMovieByIdV3(@PathVariable Long id) {
        MovieResponse findMovie = movieService.findMovieByIdV3(id);
        return ResponseEntity.ok().body(findMovie);
    }

    /**
     * 웹 메인 화면에 사용할 영화 썸네일
     */
    @GetMapping()
    public ResponseEntity<List<MovieThumbnailResponse>> findAllMovies() {
        List<MovieThumbnailResponse> movies = movieService.findAllWithThumbnailUrl();
        return ResponseEntity.ok()
                .body(movies);
    }

    /**
     * 영화 등록
     * {
     *     "title": "광해2",
     *     "runtime": 135,
     *     "ageRating": "15",
     *     "releaseDate": "2023-12-25T00:00:00",
     *     "actors": [
     *         { "name": "이병훈", "age": 42, "sns": "instagram/lbhHHH", "actorRole": "광해군" },
     *         { "name": "류승용", "age": 41, "sns": "instagram/rsRRR", "actorRole": "허균" }
     *     ],
     *     "movieFile": "testMovieFile", // Base64 encoded movie file
     *     "thumbnailFile": "testThumbnail", // Base64 encoded thumbnail
     *     "trailerFile": "testTrailer"
     * }
     */
    @PostMapping()
    public ResponseEntity<Long> createMovie(@RequestBody @Validated CreateMovieRequest request, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            //수정 필요, 검증 오류 결과에서 필요한 데이터 뽑고 별도의 API 스펙 정의해서 JSON으로 반환
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            for(ObjectError error : allErrors) {

            }
            Long bad = 1L;
            return ResponseEntity.unprocessableEntity().body(bad);
        }
        Long movieId = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(movieId);
    }

    //@RequestBody @Validated CreateMovieRequest request, BindingResult bindingResult
    //if (bindingResult.hasErrors()) {
    //log.info("검증 오류 발생 errors={}", bindingResult);
    //return bindingResult.getAllErrors();
    //}
    //CreateMovieRequest -> Bean Validation 적용 (@NotNull ...)
}
