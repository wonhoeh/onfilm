package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class UnsupportedMediaTypeException extends DomainException {

    public UnsupportedMediaTypeException() {
        super(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
}
