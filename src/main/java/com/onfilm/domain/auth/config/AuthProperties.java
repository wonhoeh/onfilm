package com.onfilm.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean accessCookieSecure,
        String accessCookieName,
        String accessCookiePath,
        String accessCookieSameSite,
        boolean csrfCookieSecure,
        String csrfCookieName,
        String csrfCookiePath,
        String csrfCookieSameSite,
        boolean refreshCookieSecure,
        String refreshCookieName,
        String refreshCookiePath,
        String refreshCookieSameSite
        ) {

    public String accessCookieNameOrDefault() {
        return (accessCookieName == null || accessCookieName.isBlank()) ? "access_token" : accessCookieName;
    }

    public String refreshCookieNameOrDefault() {
        return (refreshCookieName == null || refreshCookieName.isBlank()) ? "refresh_token" : refreshCookieName;
    }

    public String csrfCookieNameOrDefault() {
        return (csrfCookieName == null || csrfCookieName.isBlank()) ? "XSRF-TOKEN" : csrfCookieName;
    }
}
