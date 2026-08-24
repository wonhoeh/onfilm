package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class RefreshTokenReuseDetectedException extends DomainException {
    public RefreshTokenReuseDetectedException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
