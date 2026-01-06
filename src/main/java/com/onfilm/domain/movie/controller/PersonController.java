package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.CreatePersonRequest;
import com.onfilm.domain.movie.dto.MovieCardResponse;
import com.onfilm.domain.movie.dto.ProfileResponse;
import com.onfilm.domain.movie.dto.UpdatePersonRequest;
import com.onfilm.domain.movie.service.MovieReadService;
import com.onfilm.domain.movie.service.PersonReadService;
import com.onfilm.domain.movie.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;
    private final PersonReadService personReadService;
    private final MovieReadService movieReadService;

    // =============================
    // PROFILE
    // =============================
    @GetMapping("/{publicId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String publicId) {
        ProfileResponse personResponse = personReadService.getProfileByPublicId(publicId);
        return ResponseEntity.ok(personResponse);
    }

    @PostMapping()
    public ResponseEntity<Long> createPerson(@RequestBody CreatePersonRequest request) {
        Long personId = personService.createPerson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(personId);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<String> updatePerson(@PathVariable String publicId,
                                             @RequestBody UpdatePersonRequest request) {
        personService.updatePerson(publicId, request);
        return ResponseEntity.ok(publicId);
    }

    // =============================
    // FILMOGRAPHY
    // =============================
    @GetMapping("/{publicId}/movies")
    public ResponseEntity<List<MovieCardResponse>> getFilmographyByPublicId(@PathVariable String publicId) {
        List<MovieCardResponse> personFilmography = movieReadService.getFilmographyByPublicId(publicId);
        return ResponseEntity.ok(personFilmography);
    }
}
