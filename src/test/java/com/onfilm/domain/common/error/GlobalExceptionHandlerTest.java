package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.FilmographyItemNotFoundException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mediaUploadRequestNotFoundIsMappedToNotFoundResponse() {
        ResponseEntity<ErrorResponse> response = handler.handleMediaUploadRequestNotFound(
                new MediaUploadRequestNotFoundException("request-id")
        );

        assertNotFound(response, ErrorCode.MEDIA_UPLOAD_REQUEST_NOT_FOUND);
    }

    @Test
    void filmographyItemNotFoundIsMappedToNotFoundResponse() {
        ResponseEntity<ErrorResponse> response = handler.handleFilmographyItemNotFound(
                new FilmographyItemNotFoundException(1L)
        );

        assertNotFound(response, ErrorCode.FILMOGRAPHY_ITEM_NOT_FOUND);
    }

    private static void assertNotFound(
            ResponseEntity<ErrorResponse> response,
            ErrorCode errorCode
    ) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(errorCode.name());
        assertThat(response.getBody().message()).isEqualTo(errorCode.message());
    }
}
