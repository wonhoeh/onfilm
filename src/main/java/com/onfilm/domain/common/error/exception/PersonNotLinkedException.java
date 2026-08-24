package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class PersonNotLinkedException extends DomainException {

    public PersonNotLinkedException() {
        super(ErrorCode.PERSON_NOT_LINKED);
    }
}
