package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

import java.util.Objects;

/**
 * 예측 가능한 도메인 실패를 표현하는 예외의 최상위 타입이다.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).message());
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).message(), cause);
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, String message) {
        super(requireMessage(errorCode, message));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    private static String requireMessage(ErrorCode errorCode, String message) {
        requireErrorCode(errorCode);
        return Objects.requireNonNull(message, "message must not be null");
    }
}
