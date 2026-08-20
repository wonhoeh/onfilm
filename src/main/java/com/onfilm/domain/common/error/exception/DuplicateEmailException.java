package com.onfilm.domain.common.error.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("email already exists");
    }
}
