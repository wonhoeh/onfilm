package com.onfilm.domain.auth.infrastructure;

import com.onfilm.domain.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    private final AuthProperties authProperties;

    public ResponseCookie createAccessCookie(String token) {
        return ResponseCookie.from(authProperties.accessCookieNameOrDefault(), token)
                .httpOnly(true)
                .secure(authProperties.accessCookieSecure())
                .path(authProperties.accessCookiePathOrDefault())
                .sameSite(authProperties.accessCookieSameSiteOrDefault())
                .maxAge(authProperties.accessTokenTtl().toSeconds())
                .build();
    }

    public ResponseCookie createRefreshCookie(String token) {
        return ResponseCookie.from(authProperties.refreshCookieNameOrDefault(), token)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .path(authProperties.refreshCookiePathOrDefault())
                .sameSite(authProperties.refreshCookieSameSiteOrDefault())
                .maxAge(authProperties.refreshTokenTtl().toSeconds())
                .build();
    }

    public ResponseCookie createCsrfCookie() {
        return ResponseCookie.from(authProperties.csrfCookieNameOrDefault(), generateCsrfToken())
                .httpOnly(false)
                .secure(authProperties.csrfCookieSecure())
                .path(authProperties.csrfCookiePathOrDefault())
                .sameSite(authProperties.csrfCookieSameSiteOrDefault())
                .maxAge(authProperties.accessTokenTtl().toSeconds())
                .build();
    }

    public ResponseCookie deleteAccessCookie() {
        return ResponseCookie.from(authProperties.accessCookieNameOrDefault(), "")
                .httpOnly(true)
                .secure(authProperties.accessCookieSecure())
                .path(authProperties.accessCookiePathOrDefault())
                .sameSite(authProperties.accessCookieSameSiteOrDefault())
                .maxAge(0)
                .build();
    }

    public ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from(authProperties.refreshCookieNameOrDefault(), "")
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .path(authProperties.refreshCookiePathOrDefault())
                .sameSite(authProperties.refreshCookieSameSiteOrDefault())
                .maxAge(0)
                .build();
    }

    public ResponseCookie deleteCsrfCookie() {
        return ResponseCookie.from(authProperties.csrfCookieNameOrDefault(), "")
                .httpOnly(false)
                .secure(authProperties.csrfCookieSecure())
                .path(authProperties.csrfCookiePathOrDefault())
                .sameSite(authProperties.csrfCookieSameSiteOrDefault())
                .maxAge(0)
                .build();
    }

    private String generateCsrfToken() {
        byte[] buf = new byte[32];
        new java.security.SecureRandom().nextBytes(buf);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
