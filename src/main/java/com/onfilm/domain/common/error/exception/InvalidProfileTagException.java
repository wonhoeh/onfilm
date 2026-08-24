package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class InvalidProfileTagException extends DomainException {
    public InvalidProfileTagException(String message) {
        super(ErrorCode.INVALID_PROFILE_TAG, message);
    }
}
