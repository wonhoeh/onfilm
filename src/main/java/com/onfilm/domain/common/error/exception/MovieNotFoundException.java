package com.onfilm.domain.common.error.exception;

public class MovieNotFoundException extends RuntimeException{
    public MovieNotFoundException (Long id) {
        super("MOVIE NOT FOUND: " + id);
    }
}
