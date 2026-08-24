package com.onfilm.domain.kafka.entity;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.ForbiddenMediaUploadAccessException;
import com.onfilm.domain.kafka.message.EncodeJobType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUploadRequestAuthorizationTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SOURCE_KEY = "movie/1/raw/file/" + REQUEST_ID + ".mp4";

    @Test
    void uploadValidationRejectsAnotherUser() {
        MediaUploadRequest request = issue();

        assertForbidden(() -> request.validateUpload(2L, SOURCE_KEY, ISSUED_AT.plusSeconds(1)));
    }

    @Test
    void completionValidationRejectsAnotherUserOrMovie() {
        MediaUploadRequest request = issue();

        assertForbidden(() -> request.validateCompletion(
                2L,
                1L,
                EncodeJobType.MOVIE,
                SOURCE_KEY,
                "video/mp4",
                ISSUED_AT.plusSeconds(1)
        ));
    }

    private static MediaUploadRequest issue() {
        return MediaUploadRequest.issue(
                REQUEST_ID,
                1L,
                1L,
                EncodeJobType.MOVIE,
                "bucket",
                SOURCE_KEY,
                "video/mp4",
                ISSUED_AT,
                ISSUED_AT.plusSeconds(600)
        );
    }

    private static void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ForbiddenMediaUploadAccessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN_MEDIA_UPLOAD_ACCESS));
    }
}
