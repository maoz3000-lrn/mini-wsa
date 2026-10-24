package io.github.maoz3000.miniwsa.ingest;

/**
 * The ingest request exceeds a configured size limit; the client should split it.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
