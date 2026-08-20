package com.onfilm.domain.token.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashingTest {

    private final TokenHashing tokenHashing = new TokenHashing();

    @Test
    void sha256_returnsDeterministicUrlSafeFixedLengthHash() {
        String first = tokenHashing.sha256("raw-refresh-token");
        String second = tokenHashing.sha256("raw-refresh-token");

        assertThat(first).isEqualTo(second)
                .hasSize(RefreshToken.TOKEN_HASH_LENGTH)
                .matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void sha256_rejectsMissingRawToken() {
        assertThatThrownBy(() -> tokenHashing.sha256(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("raw token is required");
    }
}
