package com.onfilm.domain.auth.controller;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.auth.dto.*;
import com.onfilm.domain.auth.service.AuthService;
import com.onfilm.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
        AuthTokens tokens = authService.login(request);
        ResponseCookie accessCookie = buildAccessCookie(tokens.accessToken(), authProperties.accessTokenTtl().toSeconds());
        ResponseCookie csrfCookie = buildCsrfCookie(generateCsrfToken(), authProperties.accessTokenTtl().toSeconds());
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), authProperties.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }
        AuthTokens tokens = authService.refresh(refreshToken);
        ResponseCookie accessCookie = buildAccessCookie(tokens.accessToken(), authProperties.accessTokenTtl().toSeconds());
        ResponseCookie csrfCookie = buildCsrfCookie(generateCsrfToken(), authProperties.accessTokenTtl().toSeconds());
        ResponseCookie cookie = buildRefreshCookie(tokens.refreshToken(), authProperties.refreshTokenTtl().toSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie accessCookie = buildAccessCookie("", 0);
        ResponseCookie csrfCookie = buildCsrfCookie("", 0);
        ResponseCookie cookie = buildRefreshCookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
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

    @GetMapping("/check-username")
    public ResponseEntity<UsernameCheckResponse> checkUsername(@RequestParam String username) {
        boolean available = authService.isUsernameAvailable(username);
        return ResponseEntity.ok(new UsernameCheckResponse(available));
    }

    @GetMapping("/check-email")
    public ResponseEntity<EmailCheckResponse> checkEmail(@RequestParam String email) {
        boolean available = authService.isEmailAvailable(email);
        return ResponseEntity.ok(new EmailCheckResponse(available));
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(authProperties.refreshCookieNameOrDefault(), value)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .path(authProperties.refreshCookiePath())
                .sameSite(authProperties.refreshCookieSameSite())
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie buildAccessCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(authProperties.accessCookieNameOrDefault(), value)
                .httpOnly(true)
                .secure(authProperties.accessCookieSecure())
                .path(authProperties.accessCookiePath())
                .sameSite(authProperties.accessCookieSameSite())
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie buildCsrfCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(authProperties.csrfCookieNameOrDefault(), value)
                .httpOnly(false)
                .secure(authProperties.csrfCookieSecure())
                .path(authProperties.csrfCookiePath())
                .sameSite(authProperties.csrfCookieSameSite())
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String generateCsrfToken() {
        byte[] buf = new byte[32];
        new java.security.SecureRandom().nextBytes(buf);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
