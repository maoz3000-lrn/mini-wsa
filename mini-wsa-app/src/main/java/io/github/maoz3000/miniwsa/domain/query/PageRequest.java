package io.github.maoz3000.miniwsa.domain.query;

public record PageRequest(int limit, int offset) {

    public PageRequest {
        if (limit < 1 || offset < 0) {
            throw new InvalidQueryException("limit must be positive and offset must not be negative");
        }
    }
}
