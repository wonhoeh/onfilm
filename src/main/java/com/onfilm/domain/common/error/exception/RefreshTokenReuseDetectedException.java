package com.onfilm.domain.common.error.exception;

public class RefreshTokenReuseDetectedException extends RuntimeException {
    public RefreshTokenReuseDetectedException() {
        super("Invalid refresh token");
    }
}
