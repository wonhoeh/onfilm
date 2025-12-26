package com.onfilm.domain.movie.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicProfilePageController {
    // ✅ /onfilm/wonhoeh 로 들어오면 actor-detail-temp.html 반환
    @GetMapping("/onfilm/{username}")
    public String actorDetailPage(@PathVariable String username) {
        return "forward:/actor-detail-temp.html";
    }
}