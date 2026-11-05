package io.github.maoz3000.miniwsa.domain.error;

/**
 * An analytics query has invalid or contradictory parameters.
 */
public class InvalidQueryException extends RuntimeException {

    public InvalidQueryException(String message) {
        super(message);
    }
}
