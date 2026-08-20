package com.onfilm.domain.common.error.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }

    public InvalidRefreshTokenException(Throwable cause) {
        super("Invalid refresh token", cause);
    }
}
