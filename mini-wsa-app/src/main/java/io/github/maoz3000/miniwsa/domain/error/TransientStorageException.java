package io.github.maoz3000.miniwsa.domain.error;

/**
 * The store is unavailable, slow or overloaded. Nothing was stored; retrying later can succeed.
 */
public class TransientStorageException extends StorageException {

    public TransientStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
