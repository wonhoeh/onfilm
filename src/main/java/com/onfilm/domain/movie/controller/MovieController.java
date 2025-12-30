package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.*;
import com.onfilm.domain.movie.service.MovieService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RequiredArgsConstructor
@RequestMapping("/movies")
@RestController
public class MovieController {

    private final MovieService movieService;

    @PostMapping()
    public ResponseEntity<Long> createMovie(@RequestBody @Valid CreateMovieRequest request) {
        Long movieId = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(movieId);
    }

}

