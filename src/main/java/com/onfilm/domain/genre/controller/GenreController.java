package com.onfilm.domain.genre.controller;

import com.onfilm.domain.genre.dto.CreateGenreRequest;
import com.onfilm.domain.genre.dto.GenreResponse;
import com.onfilm.domain.genre.service.GenreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/genre")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @PostMapping()
    public ResponseEntity<String> createGenre(@RequestBody @Validated CreateGenreRequest request) {
        String genreId = genreService.createGenre(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(genreId);
    }

    @GetMapping("/{genreName}")
    public ResponseEntity<GenreResponse> findByGenreName(@PathVariable String genreName) {
        GenreResponse response = genreService.findByGenreName(genreName);
        return ResponseEntity.ok().body(response);
    }
}
