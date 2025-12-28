package com.onfilm.domain.common.error.exception;


public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(String name) {
        super("PERSON NOT FOUND: " + name);
    }

    public PersonNotFoundException(Long id) {
        super("PERSON NOT FOUND: " + id);
    }
}
