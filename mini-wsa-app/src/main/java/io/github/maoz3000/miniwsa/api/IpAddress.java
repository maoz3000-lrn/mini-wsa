package io.github.maoz3000.miniwsa.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be an IPv4 or IPv6 literal. {@code null} is considered valid.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IpAddressValidator.class)
public @interface IpAddress {

    String message() default "must be a valid IPv4 or IPv6 address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
