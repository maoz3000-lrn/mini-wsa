package io.github.maoz3000.miniwsa.ingest;

/**
 * Kafka did not acknowledge the batch in time; nothing can be promised about it, so the client must retry.
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
