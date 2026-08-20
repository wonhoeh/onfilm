package com.onfilm.domain.token.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(3600);
    private static final String TOKEN_HASH = "h".repeat(RefreshToken.TOKEN_HASH_LENGTH);

    @Test
    void issue_setsValidatedImmutableValues() {
        RefreshToken token = RefreshToken.issue(1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT);

        assertThat(token.getUserId()).isEqualTo(1L);
        assertThat(token.getCreatedAt()).isEqualTo(ISSUED_AT);
        assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void issue_rejectsInvalidIdentityHashAndExpiration() {
        assertThatThrownBy(() -> RefreshToken.issue(
                0L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
        assertThatThrownBy(() -> RefreshToken.issue(
                1L, "short", ISSUED_AT, EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tokenHash length must be 43");
        assertThatThrownBy(() -> RefreshToken.issue(
                1L,
                "!".repeat(RefreshToken.TOKEN_HASH_LENGTH),
                ISSUED_AT,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tokenHash must be URL-safe Base64");
        assertThatThrownBy(() -> RefreshToken.issue(
                1L, TOKEN_HASH, ISSUED_AT, ISSUED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after issuedAt");
    }

    @Test
    void expiration_includesExactExpirationInstant() {
        RefreshToken token = RefreshToken.issue(1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT);

        assertThat(token.isExpiredAt(EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(token.isExpiredAt(EXPIRES_AT)).isTrue();
        assertThat(token.isExpiredAt(EXPIRES_AT.plusNanos(1))).isTrue();
    }

    @Test
    void consume_marksLastUseAndRevocationTogether() {
        RefreshToken token = RefreshToken.issue(1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT);
        Instant usedAt = ISSUED_AT.plusSeconds(30);

        token.consume(usedAt);

        assertThat(token.getLastUsedAt()).isEqualTo(usedAt);
        assertThat(token.getRevokedAt()).isEqualTo(usedAt);
        assertThatThrownBy(() -> token.consume(usedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refresh token already revoked");
    }

    @Test
    void consume_rejectsExpiredToken() {
        RefreshToken token = RefreshToken.issue(1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT);

        assertThatThrownBy(() -> token.consume(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refresh token expired");
    }

    @Test
    void revoke_isIdempotentAndPreservesFirstTimestamp() {
        RefreshToken token = RefreshToken.issue(1L, TOKEN_HASH, ISSUED_AT, EXPIRES_AT);
        Instant first = ISSUED_AT.plusSeconds(10);

        token.revoke(first);
        token.revoke(first.plusSeconds(10));

        assertThat(token.getRevokedAt()).isEqualTo(first);
        assertThat(token.getLastUsedAt()).isEqualTo(first);
    }
}
