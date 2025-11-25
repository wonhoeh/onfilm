package com.onfilm.domain.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ValidationErrorResponse {
    private String field;
    private String message;
}
