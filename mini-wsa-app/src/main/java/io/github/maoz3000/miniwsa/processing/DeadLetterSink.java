package io.github.maoz3000.miniwsa.processing;

import java.util.List;

/**
 * Keeps the records processing could not store, with where they came from and why.
 */
public interface DeadLetterSink {

    /**
     * Stores the dead letters and returns only once all of them are durable, so the caller can safely commit the
     * source offsets afterwards. An empty list is a no-op.
     *
     * @throws DeadLetterPublishException the dead letters could not be stored; do not commit, retry the batch
     */
    void publish(List<DeadLetter> deadLetters);
}
