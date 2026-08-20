package com.onfilm.domain.token.repository;

import com.onfilm.domain.token.entity.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
class RefreshTokenRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void tokenHashUniqueConstraint_rejectsDuplicateHash() {
        String hash = hash('a');
        refreshTokenRepository.saveAndFlush(token(1L, hash, NOW, NOW.plusSeconds(60)));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(
                token(2L, hash, NOW, NOW.plusSeconds(60))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revokeActiveByTokenHash_isIdempotent() {
        String hash = hash('b');
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                token(1L, hash, NOW, NOW.plusSeconds(60))
        );
        Instant revokedAt = NOW.plusSeconds(10);

        assertThat(refreshTokenRepository.revokeActiveByTokenHash(hash, revokedAt))
                .isEqualTo(1);
        assertThat(refreshTokenRepository.revokeActiveByTokenHash(hash, revokedAt.plusSeconds(1)))
                .isZero();

        RefreshToken reloaded = refreshTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(reloaded.getLastUsedAt()).isEqualTo(revokedAt);
    }

    @Test
    void deleteExpiredOrRevokedBefore_keepsRecentAndActiveTokens() {
        Instant cutoff = NOW.minusSeconds(DurationHolder.THIRTY_DAYS_SECONDS);
        RefreshToken oldExpired = token(
                1L,
                hash('c'),
                NOW.minusSeconds(DurationHolder.SIXTY_DAYS_SECONDS),
                NOW.minusSeconds(DurationHolder.FORTY_DAYS_SECONDS)
        );
        RefreshToken recentExpired = token(
                1L,
                hash('d'),
                NOW.minusSeconds(DurationHolder.TWENTY_DAYS_SECONDS),
                NOW.minusSeconds(DurationHolder.TEN_DAYS_SECONDS)
        );
        RefreshToken oldRevoked = token(
                1L,
                hash('e'),
                NOW.minusSeconds(DurationHolder.SIXTY_DAYS_SECONDS),
                NOW.plusSeconds(DurationHolder.TEN_DAYS_SECONDS)
        );
        oldRevoked.revoke(NOW.minusSeconds(DurationHolder.FORTY_DAYS_SECONDS));
        RefreshToken active = token(
                1L,
                hash('f'),
                NOW.minusSeconds(DurationHolder.TEN_DAYS_SECONDS),
                NOW.plusSeconds(DurationHolder.TEN_DAYS_SECONDS)
        );
        refreshTokenRepository.saveAllAndFlush(
                java.util.List.of(oldExpired, recentExpired, oldRevoked, active)
        );

        int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);

        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getId)
                .containsExactlyInAnyOrder(recentExpired.getId(), active.getId());
    }

    private static RefreshToken token(
            Long userId,
            String hash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return RefreshToken.issue(userId, hash, issuedAt, expiresAt);
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(RefreshToken.TOKEN_HASH_LENGTH);
    }

    private static final class DurationHolder {
        private static final long TEN_DAYS_SECONDS = 10L * 24 * 60 * 60;
        private static final long TWENTY_DAYS_SECONDS = 20L * 24 * 60 * 60;
        private static final long THIRTY_DAYS_SECONDS = 30L * 24 * 60 * 60;
        private static final long FORTY_DAYS_SECONDS = 40L * 24 * 60 * 60;
        private static final long SIXTY_DAYS_SECONDS = 60L * 24 * 60 * 60;
    }
}
