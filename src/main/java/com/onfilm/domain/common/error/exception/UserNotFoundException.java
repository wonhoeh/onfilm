package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(String username) {
        super(ErrorCode.USER_NOT_FOUND);
    }

    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
