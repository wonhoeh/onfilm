package com.onfilm.domain.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawPasswordPolicyTest {

    @Test
    void validate_acceptsPasswordWithinBcryptByteLimit() {
        String password = "a".repeat(RawPasswordPolicy.BCRYPT_MAX_BYTES);

        assertThat(RawPasswordPolicy.validate(password)).isEqualTo(password);
    }

    @Test
    void validate_rejectsShortOrUtf8ValueOverBcryptByteLimit() {
        assertThatThrownBy(() -> RawPasswordPolicy.validate("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password is too short");

        assertThatThrownBy(() -> RawPasswordPolicy.validate("가".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 UTF-8 bytes");
    }
}
