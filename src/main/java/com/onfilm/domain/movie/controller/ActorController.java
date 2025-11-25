package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.CreateActorRequest;
import com.onfilm.domain.movie.service.ActorService;
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
@RequestMapping("/actors")
@RequiredArgsConstructor
public class ActorController {

    private final ActorService actorService;

    @PostMapping()
    public ResponseEntity<Long> createActor(@RequestBody @Validated CreateActorRequest request) {
        Long actorId = actorService.createActor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(actorId);
    }
}
