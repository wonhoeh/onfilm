package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class PersonNotFoundException extends DomainException {
    public PersonNotFoundException(String name) {
        super(ErrorCode.PERSON_NOT_FOUND);
    }

    public PersonNotFoundException(Long id) {
        super(ErrorCode.PERSON_NOT_FOUND);
    }
}
