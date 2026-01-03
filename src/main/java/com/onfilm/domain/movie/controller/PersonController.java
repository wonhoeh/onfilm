package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.PersonResponse;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
import com.onfilm.domain.movie.service.MovieReadService;
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
    private final MovieReadService movieReadService;

    // =============================
    // PROFILE
    // =============================
    @GetMapping("/{username}")
    public ResponseEntity<PersonResponse> getPersonByUsername(@PathVariable String username) {
        PersonResponse personResponse = personService.getPersonByUsername(username);
        return ResponseEntity.ok(personResponse);
    }

    @PostMapping()
    public ResponseEntity<Long> createPerson(@RequestBody CreatePersonRequest request) {
        Long personId = personService.createPerson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(personId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> updatePerson(@PathVariable Long id,
                                             @RequestBody UpdatePersonRequest request) {
        personService.updatePerson(id, request);
        return ResponseEntity.ok(id);
    }

    // =============================
    // FILMOGRAPHY
    // =============================
    @GetMapping("/{name}/movies")
    public ResponseEntity<List<MovieCardResponse>> getFilmographyByPersonName(@PathVariable String name) {
        List<MovieCardResponse> personFilmography = movieReadService.getFilmographyByPersonName(name);
        return ResponseEntity.ok(personFilmography);
    }
}
