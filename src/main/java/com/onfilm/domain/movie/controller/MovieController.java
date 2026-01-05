package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RequiredArgsConstructor
@RequestMapping("/api/movie")
@RestController
public class MovieController {

    private final MovieService movieService;

    @PostMapping()
    public ResponseEntity<Long> createMovie(@RequestBody @Valid CreateMovieRequest request) {
        Long movieId = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(movieId);
    }
}

