package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaUploadRequestNotFoundException extends DomainException {

    public MediaUploadRequestNotFoundException(String requestId) {
        super(ErrorCode.MEDIA_UPLOAD_REQUEST_NOT_FOUND);
    }
}
