package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MovieNotFoundException extends DomainException {
    public MovieNotFoundException (Long id) {
        super(ErrorCode.MOVIE_NOT_FOUND);
    }
}
