package com.onfilm.domain.user.entity;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UserEmail {
    public static final int MAX_LENGTH = 254;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final String value;

    private UserEmail(String value) {
        this.value = value;
    }

    public static UserEmail from(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "email is too long (max " + MAX_LENGTH + ")"
            );
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid email");
        }

        return new UserEmail(normalized);
    }

    public String value() {
        return value;
    }
}
