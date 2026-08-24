package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.*;
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

    @Test
    void authenticationAndAuthorizationExceptionsUseTheirErrorCodePolicy() {
        assertDomainError(
                handler.handleAuthenticationAndAuthorization(new AuthenticationRequiredException()),
                ErrorCode.AUTHENTICATION_REQUIRED
        );
        assertDomainError(
                handler.handleAuthenticationAndAuthorization(new InvalidCredentialsException()),
                ErrorCode.INVALID_CREDENTIALS
        );
        assertDomainError(
                handler.handleAuthenticationAndAuthorization(new ForbiddenPersonAccessException()),
                ErrorCode.FORBIDDEN_PERSON_ACCESS
        );
        assertDomainError(
                handler.handleAuthenticationAndAuthorization(new ForbiddenMovieAccessException()),
                ErrorCode.FORBIDDEN_MOVIE_ACCESS
        );
        assertDomainError(
                handler.handleAuthenticationAndAuthorization(new ForbiddenMediaUploadAccessException()),
                ErrorCode.FORBIDDEN_MEDIA_UPLOAD_ACCESS
        );
        assertDomainError(
                handler.handleAuthenticationAndAuthorization(new PersonNotLinkedException()),
                ErrorCode.PERSON_NOT_LINKED
        );
    }

    @Test
    void mediaStateExceptionsUseTheirErrorCodePolicy() {
        assertDomainError(
                handler.handleMediaState(new InvalidMediaJobStatusTransitionException()),
                ErrorCode.INVALID_MEDIA_JOB_STATUS_TRANSITION
        );
        assertDomainError(
                handler.handleMediaState(new MediaUploadAlreadyCompletedException()),
                ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED
        );
        assertDomainError(
                handler.handleMediaState(new MediaUploadRequestExpiredException()),
                ErrorCode.MEDIA_UPLOAD_REQUEST_EXPIRED
        );
    }

    @Test
    void storageAndUploadExceptionsUseTheirErrorCodePolicy() {
        assertStorageError(new InvalidStorageKeyException(), ErrorCode.INVALID_STORAGE_KEY);
        assertStorageError(new StorageKeyNotOwnedException(), ErrorCode.STORAGE_KEY_NOT_OWNED);
        assertStorageError(new MediaUploadRequestMismatchException(), ErrorCode.MEDIA_UPLOAD_REQUEST_MISMATCH);
        assertStorageError(new MediaSourceFileNotFoundException(), ErrorCode.MEDIA_SOURCE_FILE_NOT_FOUND);
        assertStorageError(new MediaOutputFileNotFoundException(), ErrorCode.MEDIA_OUTPUT_FILE_NOT_FOUND);
        assertStorageError(new UnsupportedMediaTypeException(), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertStorageError(new EmptyFileException(), ErrorCode.EMPTY_FILE);
    }

    private static void assertNotFound(
            ResponseEntity<ErrorResponse> response,
            ErrorCode errorCode
    ) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertDomainError(response, errorCode);
    }

    private static void assertDomainError(
            ResponseEntity<ErrorResponse> response,
            ErrorCode errorCode
    ) {
        assertThat(response.getStatusCode()).isEqualTo(errorCode.httpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(errorCode.name());
        assertThat(response.getBody().message()).isEqualTo(errorCode.message());
    }

    private void assertStorageError(DomainException exception, ErrorCode errorCode) {
        assertDomainError(handler.handleStorageAndUpload(exception), errorCode);
    }
}
