package com.onfilm.domain.movie.controller;

import com.onfilm.domain.movie.dto.ProfileAndPublicIdResponse;
import com.onfilm.domain.movie.service.PersonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/person")
public class PersonApiController {

    private final PersonQueryService personQueryService;

    // username -> 프로필 + publicId(필모, 갤러리 조회 키)
    @GetMapping("/{username}")
    public ResponseEntity<ProfileAndPublicIdResponse> getProfileByUsername(@PathVariable String username) {
        ProfileAndPublicIdResponse profileByUsername = personQueryService.getProfileByUsername(username);
        return ResponseEntity.ok(profileByUsername);
    }
}
