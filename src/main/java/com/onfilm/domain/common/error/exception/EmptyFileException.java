package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class EmptyFileException extends DomainException {

    public EmptyFileException() {
        super(ErrorCode.EMPTY_FILE);
    }
}
