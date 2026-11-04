package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.support.text.Utf16;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class WellFormedTextValidator implements ConstraintValidator<WellFormedText, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return Utf16.isWellFormed(value);
    }
}
