package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.error.PermanentStorageException;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;

import java.util.List;

/**
 * Persists enriched events. Implementations must be idempotent per event: saving the same event again
 * (re-delivery, retry) must not produce a second logical record.
 */
public interface EnrichedEventWriter {

    /**
     * Stores the batch in a single write.
     *
     * @return the events refused individually for bad data; every other event was stored
     * @throws TransientStorageException store unavailable or overloaded; nothing stored, retry the batch
     * @throws PermanentStorageException store rejected the batch as a whole for data reasons; retrying cannot help
     */
    SaveResult saveAll(List<EnrichedEvent> events);
}
