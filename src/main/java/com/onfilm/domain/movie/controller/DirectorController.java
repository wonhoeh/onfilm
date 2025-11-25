package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.CreateDirectorRequest;
import com.onfilm.domain.movie.service.DirectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/director")
@RequiredArgsConstructor
public class DirectorController {

    private final DirectorService directorService;

    @PostMapping()
    public ResponseEntity<Long> createDirector(@RequestBody @Validated CreateDirectorRequest request) {
        Long directorId = directorService.createDirector(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(directorId);
    }
}
