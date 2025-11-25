package com.onfilm.domain.global.error.exception;

public class ActorNotFoundException extends RuntimeException {
    public ActorNotFoundException() {
        super();
    }

    public ActorNotFoundException(String message) {
        super(message);
    }

    public ActorNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ActorNotFoundException(Throwable cause) {
        super(cause);
    }

    protected ActorNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
