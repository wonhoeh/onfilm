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

    public String accessCookiePathOrDefault() {
        return (accessCookiePath == null || accessCookiePath.isBlank()) ? "/" : accessCookiePath;
    }

    public String refreshCookiePathOrDefault() {
        return (refreshCookiePath == null || refreshCookiePath.isBlank()) ? "/auth" : refreshCookiePath;
    }

    public String csrfCookiePathOrDefault() {
        return (csrfCookiePath == null || csrfCookiePath.isBlank()) ? "/" : csrfCookiePath;
    }

    public String accessCookieSameSiteOrDefault() {
        return (accessCookieSameSite == null || accessCookieSameSite.isBlank()) ? "Lax" : accessCookieSameSite;
    }

    public String refreshCookieSameSiteOrDefault() {
        return (refreshCookieSameSite == null || refreshCookieSameSite.isBlank()) ? "Lax" : refreshCookieSameSite;
    }

    public String csrfCookieSameSiteOrDefault() {
        return (csrfCookieSameSite == null || csrfCookieSameSite.isBlank()) ? "Lax" : csrfCookieSameSite;
    }
}
