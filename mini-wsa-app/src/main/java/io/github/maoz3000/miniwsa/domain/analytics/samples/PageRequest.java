package io.github.maoz3000.miniwsa.domain.analytics.samples;

import io.github.maoz3000.miniwsa.domain.error.InvalidQueryException;

public record PageRequest(int limit, int offset) {

    public PageRequest {
        if (limit < 1 || offset < 0) {
            throw new InvalidQueryException("limit must be positive and offset must not be negative");
        }
    }
}
