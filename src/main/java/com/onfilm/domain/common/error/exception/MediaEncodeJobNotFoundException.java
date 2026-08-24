package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class MediaEncodeJobNotFoundException extends DomainException {

    public MediaEncodeJobNotFoundException(String jobId) {
        super(ErrorCode.MEDIA_ENCODE_JOB_NOT_FOUND);
    }
}
