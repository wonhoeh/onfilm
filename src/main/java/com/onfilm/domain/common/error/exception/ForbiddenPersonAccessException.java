package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class ForbiddenPersonAccessException extends DomainException {

    public ForbiddenPersonAccessException() {
        super(ErrorCode.FORBIDDEN_PERSON_ACCESS);
    }
}
