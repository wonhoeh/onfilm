package com.onfilm.domain.common.error.exception;

public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException() {
        super("username already exists");
    }
}
