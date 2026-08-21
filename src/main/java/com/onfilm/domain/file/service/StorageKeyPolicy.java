package com.onfilm.domain.file.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import com.onfilm.domain.kafka.message.EncodeJobType;

@Component
public class StorageKeyPolicy {

    private static final Pattern POSITIVE_ID_PATTERN = Pattern.compile("[1-9][0-9]*");
    private static final Pattern STORYBOARD_FILE_NAME_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "(?:\\.[a-z0-9]{1,16})?"
    );
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );
    private static final Pattern MOVIE_TRAILER_FILE_NAME_PATTERN = Pattern.compile(
            UUID_PATTERN.pattern() + "\\.[a-z0-9]{1,16}"
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
                || !POSITIVE_ID_PATTERN.matcher(segments[1]).matches()
                || !STORYBOARD_FILE_NAME_PATTERN.matcher(segments[2]).matches()) {
            throw invalidStoryboardKey();
        }
        if (!segments[1].equals(personId.toString())) {
            throw new IllegalArgumentException(
                    "storyboard image does not belong to current person"
            );
        }
    }

    public void validateMovieTrailerKey(Long movieId, String storageKey) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("movieId is required");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("trailerStorageKey is required");
        }

        String key = storageKey.trim();
        if (!key.equals(storageKey) || key.startsWith("/") || key.contains("\\")) {
            throw invalidMovieTrailerKey();
        }

        String[] segments = key.split("/", -1);
        if (!hasMovieTrailerPrefix(segments)) {
            throw invalidMovieTrailerKey();
        }

        boolean singleFile = segments.length == 4
                && MOVIE_TRAILER_FILE_NAME_PATTERN.matcher(segments[3]).matches();
        boolean hlsPlaylist = segments.length == 5
                && UUID_PATTERN.matcher(segments[3]).matches()
                && segments[4].equals("index.m3u8");
        if (!singleFile && !hlsPlaylist) {
            throw invalidMovieTrailerKey();
        }
        if (!segments[1].equals(movieId.toString())) {
            throw new IllegalArgumentException(
                    "trailer storage key does not belong to movie"
            );
        }
    }

    public void validateMediaSourceKey(Long movieId, String requestId, EncodeJobType jobType, String storageKey) {
        requireMovieContext(movieId, requestId, jobType);
        String key = requireCleanKey(storageKey, "sourceKey");
        String mediaType = switch (jobType) {
            case MOVIE -> "file";
            case TRAILER -> "trailer";
            case THUMBNAIL -> "thumbnail";
        };
        String prefix = "movie/" + movieId + "/raw/" + mediaType + "/" + requestId;
        if (!key.startsWith(prefix + ".") || key.substring(prefix.length() + 1).contains("/")) {
            throw new IllegalArgumentException("sourceKey does not belong to upload request");
        }
    }

    public void validateMediaTargetKey(Long movieId, EncodeJobType jobType, String storageKey) {
        if (movieId == null || movieId <= 0 || jobType == null) {
            throw new IllegalArgumentException("movieId and jobType are required");
        }
        String key = requireCleanKey(storageKey, "targetKey");
        String prefix = switch (jobType) {
            case MOVIE -> "movie/" + movieId + "/file/";
            case TRAILER -> "movie/" + movieId + "/trailer/";
            case THUMBNAIL -> "movie/" + movieId + "/thumbnail/";
        };
        if (!key.startsWith(prefix)) throw new IllegalArgumentException("targetKey does not belong to movie");
        if (jobType == EncodeJobType.THUMBNAIL && !key.endsWith(".jpg")) {
            throw new IllegalArgumentException("thumbnail targetKey must be a jpg");
        }
        if (jobType != EncodeJobType.THUMBNAIL && !key.endsWith("/index.m3u8")) {
            throw new IllegalArgumentException("video targetKey must be an HLS playlist");
        }
    }

    private static void requireMovieContext(Long movieId, String requestId, EncodeJobType jobType) {
        if (movieId == null || movieId <= 0) throw new IllegalArgumentException("movieId is required");
        if (jobType == null) throw new IllegalArgumentException("jobType is required");
        try {
            if (!UUID_PATTERN.matcher(requestId).matches()) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("requestId must be a canonical UUID");
        }
    }

    private static String requireCleanKey(String storageKey, String field) {
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException(field + " is required");
        String key = storageKey.trim();
        if (!key.equals(storageKey) || key.startsWith("/") || key.contains("\\") || key.contains("..")) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return key;
    }

    private static boolean hasMovieTrailerPrefix(String[] segments) {
        return (segments.length == 4 || segments.length == 5)
                && segments[0].equals("movie")
                && POSITIVE_ID_PATTERN.matcher(segments[1]).matches()
                && segments[2].equals("trailer");
    }

    private static IllegalArgumentException invalidStoryboardKey() {
        return new IllegalArgumentException("invalid storyboard image key");
    }

    private static IllegalArgumentException invalidMovieTrailerKey() {
        return new IllegalArgumentException("invalid movie trailer storage key");
    }
}
