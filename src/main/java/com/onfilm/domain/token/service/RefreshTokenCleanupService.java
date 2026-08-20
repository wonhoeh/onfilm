package com.onfilm.domain.token.service;

import com.onfilm.domain.auth.config.AuthProperties;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Scheduled(cron = "${auth.refresh-token-cleanup-cron:0 0 4 * * *}")
    @Transactional
    public int deleteOldTokens() {
        Duration retention = authProperties.refreshTokenRetention();
        if (retention == null) {
            retention = DEFAULT_RETENTION;
        }
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalStateException("refresh token retention must be positive");
        }

        Instant cutoff = clock.instant().minus(retention);
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} expired or revoked refresh tokens", deleted);
        }
        return deleted;
    }
}
