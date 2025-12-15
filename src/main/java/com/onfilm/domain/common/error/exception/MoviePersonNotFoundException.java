package com.onfilm.domain.common.error.exception;

public class MoviePersonNotFoundException extends RuntimeException {
    public MoviePersonNotFoundException() {
        super();
    }

    public MoviePersonNotFoundException(String message) {
        super(message);
    }

    public MoviePersonNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public MoviePersonNotFoundException(Throwable cause) {
        super(cause);
    }

    protected MoviePersonNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
