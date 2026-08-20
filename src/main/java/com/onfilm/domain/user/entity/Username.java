package com.onfilm.domain.user.entity;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Username {
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;
    public static final String PATTERN_VALUE = "^[a-zA-Z0-9_-]{3,20}$";

    private static final Pattern PATTERN = Pattern.compile(PATTERN_VALUE);

    private final String value;
    private final String normalized;

    private Username(String value, String normalized) {
        this.value = value;
        this.normalized = normalized;
    }

    public static Username from(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }

        String value = rawUsername.trim();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "username must be 3-20 characters using letters, numbers, _ or -"
            );
        }

        return new Username(value, value.toLowerCase(Locale.ROOT));
    }

    public String value() {
        return value;
    }

    public String normalized() {
        return normalized;
    }
}
