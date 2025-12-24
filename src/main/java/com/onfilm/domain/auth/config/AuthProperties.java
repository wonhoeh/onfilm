package com.onfilm.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean refreshCookieSecure,
        String refreshCookieName,
        String refreshCookiePath,
        String refreshCookieSameSite
) {
}
