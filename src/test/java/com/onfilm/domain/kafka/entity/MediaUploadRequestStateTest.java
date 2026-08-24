package com.onfilm.domain.kafka.entity;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.MediaUploadAlreadyCompletedException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestMismatchException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestExpiredException;
import com.onfilm.domain.kafka.message.EncodeJobType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUploadRequestStateTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String FIRST_JOB_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final String SECOND_JOB_ID = "550e8400-e29b-41d4-a716-446655440002";
    private static final String SOURCE_KEY = "movie/1/raw/file/" + REQUEST_ID + ".mp4";

    @Test
    void expiredRequestThrowsGoneDomainException() {
        MediaUploadRequest request = issue();

        assertThatThrownBy(() -> request.validateUpload(
                1L,
                SOURCE_KEY,
                ISSUED_AT.plusSeconds(601)
        )).isInstanceOfSatisfying(MediaUploadRequestExpiredException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEDIA_UPLOAD_REQUEST_EXPIRED));
        assertThat(request.getStatus()).isEqualTo(MediaUploadRequestStatus.EXPIRED);
    }

    @Test
    void completedRequestRejectsDifferentJobButAcceptsSameJob() {
        MediaUploadRequest request = issue();
        request.complete(FIRST_JOB_ID, ISSUED_AT.plusSeconds(1));

        request.complete(FIRST_JOB_ID, ISSUED_AT.plusSeconds(2));

        assertThatThrownBy(() -> request.complete(SECOND_JOB_ID, ISSUED_AT.plusSeconds(2)))
                .isInstanceOfSatisfying(MediaUploadAlreadyCompletedException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED));
        assertThat(request.getJobId()).isEqualTo(FIRST_JOB_ID);
        assertThat(request.getCompletedAt()).isEqualTo(ISSUED_AT.plusSeconds(1));
    }

    @Test
    void completionRejectsValuesThatDifferFromIssuedRequest() {
        MediaUploadRequest request = issue();

        assertThatThrownBy(() -> request.validateCompletion(
                1L,
                1L,
                EncodeJobType.MOVIE,
                SOURCE_KEY,
                "video/quicktime",
                ISSUED_AT.plusSeconds(1)
        )).isInstanceOfSatisfying(MediaUploadRequestMismatchException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEDIA_UPLOAD_REQUEST_MISMATCH));
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
}
