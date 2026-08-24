package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class ForbiddenMediaUploadAccessException extends DomainException {

    public ForbiddenMediaUploadAccessException() {
        super(ErrorCode.FORBIDDEN_MEDIA_UPLOAD_ACCESS);
    }
}
