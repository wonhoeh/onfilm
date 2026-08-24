package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}
