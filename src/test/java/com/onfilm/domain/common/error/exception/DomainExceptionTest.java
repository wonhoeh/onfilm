package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DomainExceptionTest {

    @Test
    void 에러_코드의_기본_메시지를_예외_메시지로_사용한다() {
        DomainException exception = new TestDomainException(ErrorCode.PERSON_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PERSON_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.PERSON_NOT_FOUND.message());
    }

    @Test
    void 원인_예외를_보존한다() {
        RuntimeException cause = new RuntimeException("cause");

        DomainException exception = new TestDomainException(ErrorCode.INVALID_REFRESH_TOKEN, cause);

        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void 에러_코드는_null일_수_없다() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TestDomainException(null))
                .withMessage("errorCode must not be null");
    }

    private static final class TestDomainException extends DomainException {

        private TestDomainException(ErrorCode errorCode) {
            super(errorCode);
        }

        private TestDomainException(ErrorCode errorCode, Throwable cause) {
            super(errorCode, cause);
        }
    }
}
