package com.onfilm.domain.auth.controller;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.AuthResponse;
import com.onfilm.domain.auth.dto.LoginRequest;
import com.onfilm.domain.auth.dto.MeResponse;
import com.onfilm.domain.auth.dto.SignupRequest;
import com.onfilm.domain.auth.service.AuthService;
import com.onfilm.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request);
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), authProperties.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(tokens.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }
        AuthService.AuthTokens tokens = authService.refresh(refreshToken);
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), authProperties.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(tokens.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie cookie = buildRefreshCookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Long userId = Long.valueOf(authentication.getName());
        User user = authService.getUser(userId);
        return ResponseEntity.ok(new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getAvatarUrl()
        ));
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(authProperties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .path(authProperties.refreshCookiePath())
                .sameSite(authProperties.refreshCookieSameSite())
                .maxAge(maxAgeSeconds)
                .build();
    }
}
