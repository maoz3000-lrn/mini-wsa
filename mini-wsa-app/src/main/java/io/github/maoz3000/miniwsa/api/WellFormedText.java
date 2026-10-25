package io.github.maoz3000.miniwsa.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be valid Unicode text (no unpaired UTF-16 surrogates). {@code null} is considered valid.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WellFormedTextValidator.class)
public @interface WellFormedText {

    String message() default "must not contain unpaired UTF-16 surrogates";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
