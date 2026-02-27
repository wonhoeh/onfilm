package com.onfilm.domain.movie.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicProfilePageController {
    // ✅ /{username} 또는 /onfilm/{username} 로 들어오면 actor-detail.html 반환
    @GetMapping({"/{username:[a-zA-Z0-9_-]{3,20}}", "/onfilm/{username:[a-zA-Z0-9_-]{3,20}}"})
    public String actorDetailPage(@PathVariable String username) {
        return "forward:/actor-detail.html";
    }
}
