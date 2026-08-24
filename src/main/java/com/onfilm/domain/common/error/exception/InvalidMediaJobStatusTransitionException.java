package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class InvalidMediaJobStatusTransitionException extends DomainException {

    public InvalidMediaJobStatusTransitionException() {
        super(ErrorCode.INVALID_MEDIA_JOB_STATUS_TRANSITION);
    }
}
