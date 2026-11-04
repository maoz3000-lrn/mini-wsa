package io.github.maoz3000.miniwsa.processing.deadletter;

public class DeadLetterPublishException extends RuntimeException {

    public DeadLetterPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
