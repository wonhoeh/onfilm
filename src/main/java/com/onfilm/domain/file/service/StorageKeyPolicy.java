package com.onfilm.domain.file.service;

import com.onfilm.domain.common.error.exception.InvalidStorageKeyException;
import com.onfilm.domain.common.error.exception.StorageKeyNotOwnedException;
import com.onfilm.domain.kafka.message.EncodeJobType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

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
            throw new StorageKeyNotOwnedException();
        }
    }

    public void validateMovieTrailerKey(Long movieId, String storageKey) {
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException("movieId is required");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new InvalidStorageKeyException();
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
            throw new StorageKeyNotOwnedException();
        }
    }

    public void validateMediaSourceKey(Long movieId, String requestId, EncodeJobType jobType, String storageKey) {
        requireMovieContext(movieId, requestId, jobType);
        String key = requireCleanKey(storageKey);
        String mediaType = switch (jobType) {
            case MOVIE -> "file";
            case TRAILER -> "trailer";
            case THUMBNAIL -> "thumbnail";
        };
        String prefix = "movie/" + movieId + "/raw/" + mediaType + "/" + requestId;
        if (!key.startsWith(prefix + ".") || key.substring(prefix.length() + 1).contains("/")) {
            throw new StorageKeyNotOwnedException();
        }
    }

    public void validateMediaTargetKey(Long movieId, EncodeJobType jobType, String storageKey) {
        if (movieId == null || movieId <= 0 || jobType == null) {
            throw new IllegalArgumentException("movieId and jobType are required");
        }
        String key = requireCleanKey(storageKey);
        String prefix = switch (jobType) {
            case MOVIE -> "movie/" + movieId + "/file/";
            case TRAILER -> "movie/" + movieId + "/trailer/";
            case THUMBNAIL -> "movie/" + movieId + "/thumbnail/";
        };
        if (!key.startsWith(prefix)) throw new StorageKeyNotOwnedException();
        if (jobType == EncodeJobType.THUMBNAIL && !key.endsWith(".jpg")) {
            throw new InvalidStorageKeyException();
        }
        if (jobType != EncodeJobType.THUMBNAIL && !key.endsWith("/index.m3u8")) {
            throw new InvalidStorageKeyException();
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

    private static String requireCleanKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) throw new InvalidStorageKeyException();
        String key = storageKey.trim();
        if (!key.equals(storageKey) || key.startsWith("/") || key.contains("\\") || key.contains("..")) {
            throw new InvalidStorageKeyException();
        }
        return key;
    }

    private static boolean hasMovieTrailerPrefix(String[] segments) {
        return (segments.length == 4 || segments.length == 5)
                && segments[0].equals("movie")
                && POSITIVE_ID_PATTERN.matcher(segments[1]).matches()
                && segments[2].equals("trailer");
    }

    private static InvalidStorageKeyException invalidStoryboardKey() {
        return new InvalidStorageKeyException();
    }

    private static InvalidStorageKeyException invalidMovieTrailerKey() {
        return new InvalidStorageKeyException();
    }
}
