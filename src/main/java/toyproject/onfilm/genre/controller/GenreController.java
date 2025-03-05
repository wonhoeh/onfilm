package toyproject.onfilm.genre.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import toyproject.onfilm.genre.dto.CreateGenreRequest;
import toyproject.onfilm.genre.dto.GenreResponse;
import toyproject.onfilm.genre.service.GenreService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/genre")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @PostMapping()
    public ResponseEntity<String> createGenre(@RequestBody @Validated CreateGenreRequest request, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            //수정 필요, 검증 오류 결과에서 필요한 데이터 뽑고 별도의 API 스펙 정의해서 JSON으로 반환
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            for(ObjectError error : allErrors) {
                log.info(String.valueOf(error));
            }
            return ResponseEntity.unprocessableEntity().body("bad");
        }
        String genreId = genreService.createGenre(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(genreId);
    }

    @GetMapping("/{genreName}")
    public ResponseEntity<GenreResponse> findByGenreName(@PathVariable String genreName) {
        GenreResponse response = genreService.findByGenreName(genreName);
        return ResponseEntity.ok().body(response);
    }
}
