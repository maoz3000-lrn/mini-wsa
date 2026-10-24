package io.github.maoz3000.miniwsa.ingest;

/**
 * One problem found in an ingest request.
 *
 * @param index   position of the event in the batch, or {@code null} for request-level problems
 * @param field   dotted path of the offending field, or {@code null} when not field-specific
 * @param message what is wrong
 */
public record EventError(Integer index, String field, String message) {

    public static EventError ofRequest(String message) {
        return new EventError(null, null, message);
    }
}
