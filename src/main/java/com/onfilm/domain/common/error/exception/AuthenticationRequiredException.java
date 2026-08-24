package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class AuthenticationRequiredException extends DomainException {

    public AuthenticationRequiredException() {
        super(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
