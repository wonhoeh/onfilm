package com.onfilm.domain.movie.controller;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.UnsupportedMediaTypeException;
import com.onfilm.domain.file.service.StorageKeyFactory;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.PresignUploadRequest;
import com.onfilm.domain.kafka.service.MediaEncodeJobCommandService;
import com.onfilm.domain.kafka.service.MediaUploadRequestService;
import com.onfilm.domain.movie.service.MovieMediaService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MovieFileControllerMediaTypeTest {

    @Test
    void presignRejectsUnsupportedVideoContentType() {
        StorageService storage = mock(StorageService.class);
        StorageKeyFactory keyFactory = mock(StorageKeyFactory.class);
        MovieMediaService movieMediaService = mock(MovieMediaService.class);
        MediaEncodeJobCommandService commandService = mock(MediaEncodeJobCommandService.class);
        MediaUploadRequestService uploadRequestService = mock(MediaUploadRequestService.class);
        MovieFileController controller = new MovieFileController(
                storage,
                keyFactory,
                movieMediaService,
                commandService,
                uploadRequestService
        );
        given(uploadRequestService.newRequestId())
                .willReturn("550e8400-e29b-41d4-a716-446655440000");

        assertThatThrownBy(() -> controller.presignTrailerUpload(
                1L,
                new PresignUploadRequest("text/plain")
        )).isInstanceOfSatisfying(UnsupportedMediaTypeException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }
}
