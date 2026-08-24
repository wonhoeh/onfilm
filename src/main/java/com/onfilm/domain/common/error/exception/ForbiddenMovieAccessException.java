package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class ForbiddenMovieAccessException extends DomainException {

    public ForbiddenMovieAccessException() {
        super(ErrorCode.FORBIDDEN_MOVIE_ACCESS);
    }
}
