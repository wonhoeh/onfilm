package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.FilmographyResponse;
import com.onfilm.domain.movie.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/persons")
public class PersonController {

    private final PersonService personService;

    @PostMapping()
    public ResponseEntity<Long> createPerson(@RequestBody CreatePersonRequest request) {
        Long personId = personService.createPerson(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(personId);
    }

    @GetMapping("/{id}/filmography")
    public ResponseEntity<List<FilmographyResponse>> getFilmography(@PathVariable Long id) {
        List<FilmographyResponse> filmographyResponses = personService.getFilmography(id).stream()
                .map(FilmographyResponse::new)
                .toList();
        return ResponseEntity.ok(filmographyResponses);
    }
}
