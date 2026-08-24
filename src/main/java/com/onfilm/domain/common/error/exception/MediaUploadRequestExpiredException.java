package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaUploadRequestExpiredException extends DomainException {

    public MediaUploadRequestExpiredException() {
        super(ErrorCode.MEDIA_UPLOAD_REQUEST_EXPIRED);
    }
}
