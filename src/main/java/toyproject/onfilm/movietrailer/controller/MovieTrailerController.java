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
    public ResponseEntity<Long> createTrailer(@RequestBody @Validated CreateTrailerRequest request) {
        Long trailerId = movieTrailerService.createTrailer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(trailerId);
    }
}
