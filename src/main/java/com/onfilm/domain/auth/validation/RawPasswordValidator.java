package com.onfilm.domain.auth.validation;

import com.onfilm.domain.user.entity.RawPasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RawPasswordValidator implements ConstraintValidator<ValidRawPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            RawPasswordPolicy.validate(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
