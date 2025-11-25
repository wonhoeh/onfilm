package com.onfilm.domain.global.error.exception;

public class WriterNotFoundException extends RuntimeException {
    public WriterNotFoundException() {
        super();
    }

    public WriterNotFoundException(String message) {
        super(message);
    }

    public WriterNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public WriterNotFoundException(Throwable cause) {
        super(cause);
    }

    protected WriterNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
