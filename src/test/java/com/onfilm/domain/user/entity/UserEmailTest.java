package com.onfilm.domain.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserEmailTest {

    @Test
    void from_trimsAndNormalizesCase() {
        UserEmail email = UserEmail.from("  User.Name@Example.COM  ");

        assertThat(email.value()).isEqualTo("user.name@example.com");
    }

    @Test
    void from_rejectsMissingInvalidAndOverlongValues() {
        assertThatThrownBy(() -> UserEmail.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email is required");
        assertThatThrownBy(() -> UserEmail.from("invalid-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid email");
        assertThatThrownBy(() -> UserEmail.from(
                "a".repeat(UserEmail.MAX_LENGTH) + "@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email is too long");
    }
}
