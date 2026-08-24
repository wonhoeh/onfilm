package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaSourceFileNotFoundException extends DomainException {

    public MediaSourceFileNotFoundException() {
        super(ErrorCode.MEDIA_SOURCE_FILE_NOT_FOUND);
    }
}
