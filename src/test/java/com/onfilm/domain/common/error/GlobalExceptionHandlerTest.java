package com.onfilm.domain.common.error;

import com.onfilm.domain.common.error.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import jakarta.validation.ConstraintViolationException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void domainExceptionsUseErrorCodeAsTheSingleResponsePolicy() {
        List<ExpectedResponse> expectations = List.of(
                expected(new PersonNotFoundException(1L), ErrorCode.PERSON_NOT_FOUND),
                expected(new AuthenticationRequiredException(), ErrorCode.AUTHENTICATION_REQUIRED),
                expected(new ForbiddenPersonAccessException(), ErrorCode.FORBIDDEN_PERSON_ACCESS),
                expected(new InvalidMediaJobStatusTransitionException(), ErrorCode.INVALID_MEDIA_JOB_STATUS_TRANSITION),
                expected(new MediaUploadRequestExpiredException(), ErrorCode.MEDIA_UPLOAD_REQUEST_EXPIRED),
                expected(new UnsupportedMediaTypeException(), ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        );

        assertThat(expectations).allSatisfy(expectation -> assertDomainError(
                handler.handleDomainException(expectation.exception()),
                expectation.errorCode()
        ));
    }

    @Test
    void domainResponseDoesNotExposeDetailedInternalMessage() {
        InvalidProfileTagException exception = new InvalidProfileTagException("tag is required");

        ResponseEntity<ErrorResponse> response = handler.handleDomainException(exception);

        assertThat(exception.getMessage()).isEqualTo("tag is required");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_PROFILE_TAG.message());
        assertThat(response.getBody().message()).doesNotContain("tag is required");
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

    @Test
    void illegalArgumentDoesNotExposeInternalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("INTERNAL_ARGUMENT_DETAIL")
        );

        assertDomainError(response, ErrorCode.BAD_REQUEST);
        assertThat(response.getBody().message()).doesNotContain("INTERNAL_ARGUMENT_DETAIL");
    }

    @Test
    void illegalStateIsNotConvertedToClientBadRequest() {
        ExceptionHandlerMethodResolver resolver =
                new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

        assertThat(resolver.resolveMethod(new IllegalStateException("INTERNAL_STATE_DETAIL")))
                .isNull();
    }

    @Test
    void frameworkExceptionsUseErrorCodeAsTheSingleResponsePolicy() {
        List<ExpectedResponseEntity> expectations = List.of(
                expected(
                        handler.handleConstraintViolation(new ConstraintViolationException(Set.of())),
                        ErrorCode.VALIDATION_FAILED
                ),
                expected(
                        handler.handleDataIntegrityViolation(new DataIntegrityViolationException("constraint")),
                        ErrorCode.DATA_INTEGRITY_VIOLATION
                ),
                expected(
                        handler.handleOptimisticLock(new OptimisticLockingFailureException("conflict")),
                        ErrorCode.CONCURRENT_MEDIA_JOB_UPDATE
                )
        );

        assertThat(expectations).allSatisfy(expectation -> assertDomainError(
                expectation.response(),
                expectation.errorCode()
        ));
    }

    private static ExpectedResponse expected(DomainException exception, ErrorCode errorCode) {
        return new ExpectedResponse(exception, errorCode);
    }

    private static ExpectedResponseEntity expected(
            ResponseEntity<ErrorResponse> response,
            ErrorCode errorCode
    ) {
        return new ExpectedResponseEntity(response, errorCode);
    }

    private record ExpectedResponse(DomainException exception, ErrorCode errorCode) {
    }

    private record ExpectedResponseEntity(
            ResponseEntity<ErrorResponse> response,
            ErrorCode errorCode
    ) {
    }
}
