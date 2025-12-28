package com.onfilm.domain.common.error.exception;


public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(String name) {
        super("PERSON NOT FOUND: " + name);
    }
}
