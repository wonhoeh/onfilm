package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaUploadAlreadyCompletedException extends DomainException {

    public MediaUploadAlreadyCompletedException() {
        super(ErrorCode.MEDIA_UPLOAD_ALREADY_COMPLETED);
    }
}
