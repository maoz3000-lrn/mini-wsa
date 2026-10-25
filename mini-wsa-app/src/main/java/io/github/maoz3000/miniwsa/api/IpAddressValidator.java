package io.github.maoz3000.miniwsa.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.InetAddress;

/**
 * Parses the value as an IP literal only; never performs DNS resolution.
 */
public class IpAddressValidator implements ConstraintValidator<IpAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isIpLiteral(value);
    }

    private static boolean isIpLiteral(String value) {
        return (value.contains(":") || isDottedQuad(value)) && parsesAsLiteral(value);
    }

    // InetAddress.ofLiteral also accepts the legacy short forms "a.b.c", "a.b" and "a"
    private static boolean isDottedQuad(String value) {
        return value.chars().filter(c -> c == '.').count() == 3;
    }

    private static boolean parsesAsLiteral(String value) {
        try {
            InetAddress.ofLiteral(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
