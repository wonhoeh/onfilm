package com.onfilm.domain.genre.controller;

import com.onfilm.domain.genre.dto.GenreAutocompleteResponse;
import com.onfilm.domain.genre.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping("/autocomplete")
    public ResponseEntity<List<GenreAutocompleteResponse>> autocomplete(
            @RequestParam(defaultValue = "") String query
    ) {
        return ResponseEntity.ok(genreService.autocomplete(query));
    }
}
