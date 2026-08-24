package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class InvalidStorageKeyException extends DomainException {

    public InvalidStorageKeyException() {
        super(ErrorCode.INVALID_STORAGE_KEY);
    }
}
