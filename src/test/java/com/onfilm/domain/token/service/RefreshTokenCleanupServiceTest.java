package com.onfilm.domain.token.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void deleteOldTokens_usesConfiguredRetentionAndClock() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        AuthProperties properties = mock(AuthProperties.class);
        when(properties.refreshTokenRetention()).thenReturn(Duration.ofDays(30));
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        when(repository.deleteExpiredOrRevokedBefore(cutoff)).thenReturn(3);
        RefreshTokenCleanupService service = new RefreshTokenCleanupService(
                repository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.deleteOldTokens()).isEqualTo(3);
        verify(repository).deleteExpiredOrRevokedBefore(cutoff);
    }

    @Test
    void deleteOldTokens_rejectsNonPositiveRetention() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        AuthProperties properties = mock(AuthProperties.class);
        when(properties.refreshTokenRetention()).thenReturn(Duration.ZERO);
        RefreshTokenCleanupService service = new RefreshTokenCleanupService(
                repository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(service::deleteOldTokens)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refresh token retention must be positive");
    }
}
