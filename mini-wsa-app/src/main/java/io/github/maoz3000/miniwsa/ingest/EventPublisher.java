package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.domain.IngestedEvent;

import java.util.List;

/**
 * Hands accepted events to the processing pipeline.
 */
public interface EventPublisher {

    /**
     * Publishes every event and returns only once all of them are durably stored, so the caller may confirm
     * the batch to the client. Re-publishing an event is safe: processing is idempotent per eventId.
     *
     * @throws EventPublishException the events could not be stored in time; the caller should ask the client to retry
     */
    void publish(List<IngestedEvent> events);
}
