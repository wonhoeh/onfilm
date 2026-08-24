package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.service.MovieCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RequiredArgsConstructor
@RequestMapping("/api/movie")
@RestController
public class MovieController {

    private final MovieCommandService movieCommandService;

    @PostMapping()
    public ResponseEntity<Long> createMovie(@RequestBody @Valid CreateMovieRequest request) {
        Long movieId = movieCommandService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(movieId);
    }
}
