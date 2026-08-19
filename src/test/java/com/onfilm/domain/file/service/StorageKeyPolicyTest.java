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

    private void assertInvalid(String key) {
        assertThatThrownBy(() -> storageKeyPolicy.validateStoryboardCardKey(2L, key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid storyboard image key");
    }
}
