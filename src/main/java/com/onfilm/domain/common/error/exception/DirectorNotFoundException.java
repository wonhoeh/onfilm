package com.onfilm.domain.common.error.exception;

public class DirectorNotFoundException extends RuntimeException {
    public DirectorNotFoundException() {
        super();
    }

    public DirectorNotFoundException(String message) {
        super(message);
    }

    public DirectorNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public DirectorNotFoundException(Throwable cause) {
        super(cause);
    }

    protected DirectorNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
