package com.onfilm.domain.common.error.exception;

import com.onfilm.domain.common.error.ErrorCode;

public class FilmographyFileNotFoundException extends DomainException {

    public FilmographyFileNotFoundException(String publicId) {
        super(ErrorCode.FILMOGRAPHY_FILE_NOT_FOUND);
    }
}
