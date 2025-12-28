package com.onfilm.domain.common.error.exception;


public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String username) {
        super("USER NOT FOUND " + username);
    }

    public UserNotFoundException(Long id) {
        super("USER NOT FOUND: " + id);
    }
}
