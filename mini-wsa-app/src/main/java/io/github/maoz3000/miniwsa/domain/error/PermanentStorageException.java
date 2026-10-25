package io.github.maoz3000.miniwsa.domain.error;

/**
 * The store rejected the data itself; retrying the same batch will fail again.
 */
public class PermanentStorageException extends StorageException {

    public PermanentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
