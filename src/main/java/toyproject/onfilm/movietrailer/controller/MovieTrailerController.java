package toyproject.onfilm.movietrailer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import toyproject.onfilm.movietrailer.dto.CreateTrailerRequest;
import toyproject.onfilm.movietrailer.service.MovieTrailerService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/trailer")
public class MovieTrailerController {

    private final MovieTrailerService movieTrailerService;

    @PostMapping()
    public ResponseEntity<Long> createTrailer(@RequestBody @Validated CreateTrailerRequest request, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            //수정 필요, 검증 오류 결과에서 필요한 데이터 뽑고 별도의 API 스펙 정의해서 JSON으로 반환
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            for(ObjectError error : allErrors) {
                log.info(String.valueOf(error));
            }
            Long bad = 1L;
            return ResponseEntity.unprocessableEntity().body(bad);
        }
        Long trailerId = movieTrailerService.createTrailer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(trailerId);
    }
}
