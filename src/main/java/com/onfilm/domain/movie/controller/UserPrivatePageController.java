package com.onfilm.domain.movie.controller;

import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class UserPrivatePageController {

    private static final String USERNAME_PATTERN = "[a-zA-Z0-9_-]{3,20}";

    private final UserRepository userRepository;

    @GetMapping({
            "/edit-profile",
            "/edit-filmography",
            "/edit-gallery",
            "/storyboard",
            "/edit-storyboard",
            "/storyboard-view"
    })
    public String redirectToUserScoped(HttpServletRequest request) {
        String username = currentUsernameOrNull();
        if (username == null) {
            return "redirect:" + buildLoginRedirect(request);
        }
        String path = request.getRequestURI();
        String suffix = path.startsWith("/") ? path.substring(1) : path;
        String qs = request.getQueryString();
        String target = "/" + username + "/" + suffix + (qs == null || qs.isBlank() ? "" : "?" + qs);
        return "redirect:" + target;
    }

    @GetMapping({
            "/{username:" + USERNAME_PATTERN + "}/edit-profile",
            "/{username:" + USERNAME_PATTERN + "}/edit-filmography",
            "/{username:" + USERNAME_PATTERN + "}/edit-gallery",
            "/{username:" + USERNAME_PATTERN + "}/storyboard",
            "/{username:" + USERNAME_PATTERN + "}/edit-storyboard",
            "/{username:" + USERNAME_PATTERN + "}/storyboard-view"
    })
    public String forwardPrivatePages(@PathVariable String username, HttpServletRequest request) {
        String current = currentUsernameOrNull();
        if (current == null) {
            return "redirect:" + buildLoginRedirect(request);
        }
        if (current != null && !current.equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        String path = request.getRequestURI();
        if (path.endsWith("/edit-profile")) return "forward:/edit-profile.html";
        if (path.endsWith("/edit-filmography")) return "forward:/edit-filmography.html";
        if (path.endsWith("/edit-gallery")) return "forward:/edit-gallery.html";
        if (path.endsWith("/storyboard")) return "forward:/storyboard.html";
        if (path.endsWith("/edit-storyboard")) return "forward:/edit-storyboard.html";
        if (path.endsWith("/storyboard-view")) return "forward:/storyboard-view.html";
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private String currentUsernameOrNull() {
        try {
            Long userId = SecurityUtil.currentUserId();
            User user = userRepository.findById(userId)
                    .orElse(null);
            if (user == null) return null;
            String username = user.getUsername();
            if (username == null || username.isBlank()) return null;
            return username.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildLoginRedirect(HttpServletRequest request) {
        String path = request.getRequestURI();
        String qs = request.getQueryString();
        String next = path + (qs == null || qs.isBlank() ? "" : "?" + qs);
        String encoded = URLEncoder.encode(next, StandardCharsets.UTF_8);
        return "/login.html?next=" + encoded;
    }
}
