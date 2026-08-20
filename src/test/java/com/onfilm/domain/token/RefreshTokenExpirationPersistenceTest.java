package com.onfilm.domain.token;

import com.onfilm.domain.common.error.exception.InvalidRefreshTokenException;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.entity.TokenHashing;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import com.onfilm.domain.token.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RefreshTokenExpirationPersistenceTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private TokenHashing tokenHashing;
    @Autowired
    private Clock clock;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
    }

    @Test
    void expiredAccessRecord_commitsDespiteOuterUnauthorizedRollback() {
        String rawToken = "expired-refresh-token";
        Instant now = clock.instant();
        RefreshToken token = RefreshToken.issue(
                1L,
                tokenHashing.sha256(rawToken),
                now.minus(Duration.ofDays(2)),
                now.minus(Duration.ofDays(1))
        );
        Long tokenId = refreshTokenRepository.saveAndFlush(token).getId();

        assertThatThrownBy(() -> refreshTokenService.rotate(
                rawToken,
                Duration.ofDays(14)
        )).isInstanceOf(InvalidRefreshTokenException.class);

        RefreshToken reloaded = refreshTokenRepository.findById(tokenId).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isNotNull();
        assertThat(reloaded.getLastUsedAt()).isEqualTo(reloaded.getRevokedAt());
        assertThat(reloaded.getRevokedAt()).isAfterOrEqualTo(now);
    }
}
