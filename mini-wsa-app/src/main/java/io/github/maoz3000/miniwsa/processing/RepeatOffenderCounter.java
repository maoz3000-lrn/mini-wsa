package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;

import java.util.List;

/**
 * Remembers recent events per client IP across batches and processes, to detect repeat offenders.
 */
public interface RepeatOffenderCounter {

    /**
     * Records the events and returns, in the same order, how many earlier events of the same client IP fall in
     * each event's window. Callers should pass events sorted by timestamp. Recording an event again (same eventId)
     * changes nothing, so a batch can safely be processed twice.
     *
     * @throws TransientStorageException the store is unavailable; retry the batch
     */
    List<Long> recordAndCountPrior(List<DlrEvent> events);
}
