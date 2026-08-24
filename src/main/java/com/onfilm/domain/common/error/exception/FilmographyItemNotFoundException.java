package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class FilmographyItemNotFoundException extends DomainException {

    public FilmographyItemNotFoundException(Long movieId) {
        super(ErrorCode.FILMOGRAPHY_ITEM_NOT_FOUND);
    }
}
