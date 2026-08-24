package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaUploadRequestMismatchException extends DomainException {

    public MediaUploadRequestMismatchException() {
        super(ErrorCode.MEDIA_UPLOAD_REQUEST_MISMATCH);
    }
}
