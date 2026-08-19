package com.onfilm.domain.file.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class StorageKeyPolicy {

    private static final Pattern PERSON_ID_PATTERN = Pattern.compile("[1-9][0-9]*");
    private static final Pattern STORYBOARD_FILE_NAME_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "(?:\\.[a-z0-9]{1,16})?"
    );

    public void validateStoryboardCardKey(Long personId, String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }
        if (personId == null || personId <= 0) {
            throw new IllegalArgumentException("personId is required");
        }

        String key = imageKey.trim();
        if (!key.equals(imageKey) || key.startsWith("/") || key.contains("\\")) {
            throw invalidStoryboardKey();
        }

        String[] segments = key.split("/", -1);
        if (segments.length != 3
                || !segments[0].equals("storyboard")
                || !PERSON_ID_PATTERN.matcher(segments[1]).matches()
                || !STORYBOARD_FILE_NAME_PATTERN.matcher(segments[2]).matches()) {
            throw invalidStoryboardKey();
        }
        if (!segments[1].equals(personId.toString())) {
            throw new IllegalArgumentException(
                    "storyboard image does not belong to current person"
            );
        }
    }

    private static IllegalArgumentException invalidStoryboardKey() {
        return new IllegalArgumentException("invalid storyboard image key");
    }
}
