package com.onfilm.domain.user.entity;

import java.nio.charset.StandardCharsets;

public final class RawPasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int BCRYPT_MAX_BYTES = 72;

    private RawPasswordPolicy() {
    }

    public static String validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "password is too short (min " + MIN_LENGTH + ")"
            );
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "password is too long for BCrypt (max "
                            + BCRYPT_MAX_BYTES + " UTF-8 bytes)"
            );
        }
        return rawPassword;
    }
}
