package com.onfilm.domain.file.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeyPolicyTest {

    private final StorageKeyFactory storageKeyFactory = new StorageKeyFactory();
    private final StorageKeyPolicy storageKeyPolicy = new StorageKeyPolicy();

    @Test
    void acceptsServerIssuedKeyOwnedByCurrentPerson() {
        String key = storageKeyFactory.storyboardCard(2L, ".jpeg");

        assertThatCode(() -> storageKeyPolicy.validateStoryboardCardKey(2L, key))
                .doesNotThrowAnyException();
        assertThatCode(() -> storageKeyPolicy.validateStoryboardCardKey(2L, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> storageKeyPolicy.validateStoryboardCardKey(2L, "   "))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsKeyOwnedByAnotherPerson() {
        String key = storageKeyFactory.storyboardCard(1L, ".jpg");

        assertThatThrownBy(() -> storageKeyPolicy.validateStoryboardCardKey(2L, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("storyboard image does not belong to current person");
    }

    @Test
    void rejectsUrlAbsoluteTraversalAndOtherNamespaceKeys() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000.jpg";

        assertInvalid("https://cdn.example.com/storyboard/2/" + uuid);
        assertInvalid("/storyboard/2/" + uuid);
        assertInvalid("storyboard\\2\\" + uuid);
        assertInvalid("storyboard/2/../1/" + uuid);
        assertInvalid("gallery/2/" + uuid);
        assertInvalid(" storyboard/2/" + uuid);
    }

    @Test
    void rejectsKeysThatWereNotIssuedInStoryboardFormat() {
        assertInvalid("storyboard/2/not-a-uuid.jpg");
        assertInvalid("storyboard/2/550e8400-e29b-41d4-a716-446655440000.exe.sh");
        assertInvalid("storyboard/02/550e8400-e29b-41d4-a716-446655440000.jpg");
        assertInvalid("storyboard/2/");
    }

    @Test
    void acceptsMovieTrailerFileAndHlsKeysOwnedByMovie() {
        String fileKey = storageKeyFactory.movieTrailer(2L, ".mp4");
        String hlsKey = storageKeyFactory.movieTrailerHlsTarget(2L);

        assertThatCode(() -> storageKeyPolicy.validateMovieTrailerKey(2L, fileKey))
                .doesNotThrowAnyException();
        assertThatCode(() -> storageKeyPolicy.validateMovieTrailerKey(2L, hlsKey))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMovieTrailerKeyOwnedByAnotherMovie() {
        String key = storageKeyFactory.movieTrailer(1L, ".mp4");

        assertThatThrownBy(() -> storageKeyPolicy.validateMovieTrailerKey(2L, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("trailer storage key does not belong to movie");
    }

    @Test
    void rejectsUrlTraversalAndInvalidMovieTrailerKeys() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000.mp4";

        assertInvalidMovieTrailerKey("https://cdn.example.com/movie/2/trailer/" + uuid);
        assertInvalidMovieTrailerKey("/movie/2/trailer/" + uuid);
        assertInvalidMovieTrailerKey("movie/2/trailer/../" + uuid);
        assertInvalidMovieTrailerKey("movie/2/file/" + uuid);
        assertInvalidMovieTrailerKey("movie/2/trailer/not-a-uuid.mp4");
        assertInvalidMovieTrailerKey(
                "movie/2/trailer/550e8400-e29b-41d4-a716-446655440000/master.m3u8"
        );
    }

    private void assertInvalid(String key) {
        assertThatThrownBy(() -> storageKeyPolicy.validateStoryboardCardKey(2L, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid storyboard image key");
    }

    private void assertInvalidMovieTrailerKey(String key) {
        assertThatThrownBy(() -> storageKeyPolicy.validateMovieTrailerKey(2L, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid movie trailer storage key");
    }
}
