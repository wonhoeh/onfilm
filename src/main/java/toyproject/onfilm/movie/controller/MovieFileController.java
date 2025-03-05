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
                                                   @RequestBody @Validated UploadMovieFileRequest request,
                                                   BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            //수정 필요, 검증 오류 결과에서 필요한 데이터 뽑고 별도의 API 스펙 정의해서 JSON으로 반환
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            for(ObjectError error : allErrors) {

            }
            return ResponseEntity.unprocessableEntity().body("bad");
        }

        movieService.uploadMovieFiles(movieId, request);
        return ResponseEntity.ok("파일이 업로드되었습니다.");

    }
}
