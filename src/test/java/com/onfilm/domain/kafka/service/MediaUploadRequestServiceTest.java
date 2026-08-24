package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.MediaUploadRequestNotFoundException;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MediaUploadRequestServiceTest {

    @Test
    void rawUploadAuthorizationThrowsNotFoundWhenRequestDoesNotExist() {
        MediaPresignedUploadService presignedUploadService = mock(MediaPresignedUploadService.class);
        MediaUploadRequestRepository repository = mock(MediaUploadRequestRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        MediaUploadRequestService service = new MediaUploadRequestService(
                presignedUploadService,
                repository,
                clock
        );
        String requestId = "550e8400-e29b-41d4-a716-446655440000";
        String sourceKey = "movie/1/raw/file/" + requestId + ".mp4";
        given(repository.findByIdForUpdate(requestId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorizeRawUpload(1L, sourceKey))
                .isInstanceOfSatisfying(MediaUploadRequestNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_REQUEST_NOT_FOUND));
    }
}
