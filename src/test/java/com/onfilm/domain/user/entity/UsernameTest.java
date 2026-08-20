package com.onfilm.domain.user.entity;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsernameTest {

    @Test
    void from_preservesDisplayValueAndCreatesCaseInsensitiveIdentity() {
        Username username = Username.from("  Test_User-1  ");

        assertThat(username.value()).isEqualTo("Test_User-1");
        assertThat(username.normalized()).isEqualTo("test_user-1");
    }

    @Test
    void from_usesLocaleRoot() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(Username.from("INDIE").normalized()).isEqualTo("indie");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void from_rejectsMissingInvalidAndOutOfRangeValues() {
        assertThatThrownBy(() -> Username.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username is required");
        assertThatThrownBy(() -> Username.from("ab"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Username.from("a".repeat(Username.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Username.from("invalid name"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
