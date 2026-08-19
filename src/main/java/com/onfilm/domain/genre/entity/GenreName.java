package com.onfilm.domain.genre.entity;

import java.text.Normalizer;
import java.util.Locale;

public final class GenreName {

    public static final int MAX_LENGTH = 60;

    private final String displayName;
    private final String normalized;

    private GenreName(String displayName, String normalized) {
        this.displayName = displayName;
        this.normalized = normalized;
    }

    public static GenreName from(String rawName) {
        String displayName = sanitize(rawName);
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("genre name is required");
        }
        validateLength(displayName, "genre name");

        String normalized = displayName.toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("normalized genre name is required");
        }
        validateLength(normalized, "normalized genre name");
        return new GenreName(displayName, normalized);
    }

    public static String normalize(String rawName) {
        String displayName = sanitize(rawName);
        if (displayName.isBlank()) {
            return "";
        }
        validateLength(displayName, "genre name");

        String normalized = displayName.toLowerCase(Locale.ROOT);
        validateLength(normalized, "normalized genre name");
        return normalized;
    }

    public String displayName() {
        return displayName;
    }

    public String normalized() {
        return normalized;
    }

    private static String sanitize(String rawName) {
        if (rawName == null) {
            return "";
        }

        String normalizedUnicode = Normalizer.normalize(
                rawName,
                Normalizer.Form.NFKC
        );
        return normalizedUnicode
                .trim()
                .replaceFirst("^#+", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static void validateLength(String value, String fieldName) {
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " is too long (max " + MAX_LENGTH + ")"
            );
        }
    }
}
