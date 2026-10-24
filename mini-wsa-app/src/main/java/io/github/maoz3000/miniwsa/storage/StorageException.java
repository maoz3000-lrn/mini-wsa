package io.github.maoz3000.miniwsa.storage;

/**
 * A store (ClickHouse, Redis) failed. Subclasses tell callers whether retrying can help.
 */
public abstract class StorageException extends RuntimeException {

    protected StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
