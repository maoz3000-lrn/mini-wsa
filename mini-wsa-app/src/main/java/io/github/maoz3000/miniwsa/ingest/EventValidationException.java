package io.github.maoz3000.miniwsa.ingest;

import java.util.List;

/**
 * The ingest request was rejected as a whole because of the listed problems.
 */
public class EventValidationException extends RuntimeException {

    private final transient List<EventError> errors;

    public EventValidationException(List<EventError> errors) {
        super(errors.size() + " validation error(s); batch rejected");
        this.errors = List.copyOf(errors);
    }

    public List<EventError> errors() {
        return errors;
    }
}
