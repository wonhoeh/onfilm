package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaOutputFileNotFoundException extends DomainException {

    public MediaOutputFileNotFoundException() {
        super(ErrorCode.MEDIA_OUTPUT_FILE_NOT_FOUND);
    }
}
