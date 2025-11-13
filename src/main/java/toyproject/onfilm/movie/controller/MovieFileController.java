package toyproject.onfilm.movie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import toyproject.onfilm.movie.dto.UploadMovieFileRequest;
import toyproject.onfilm.movie.service.MovieService;

import java.util.List;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieFileController {

    private final MovieService movieService;

    @PostMapping("/{movieId}/files")
    public ResponseEntity<String> uploadMovieFiles(@PathVariable Long movieId,
                                                   @RequestBody @Validated UploadMovieFileRequest request) {
        movieService.uploadMovieFiles(movieId, request);
        return ResponseEntity.ok("파일이 업로드되었습니다.");
    }
}
