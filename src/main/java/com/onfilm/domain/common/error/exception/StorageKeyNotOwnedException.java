package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class StorageKeyNotOwnedException extends DomainException {

    public StorageKeyNotOwnedException() {
        super(ErrorCode.STORAGE_KEY_NOT_OWNED);
    }
}
