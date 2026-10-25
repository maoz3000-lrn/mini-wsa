package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.domain.IngestedEvent;

/**
 * An event processing could not store, and why.
 *
 * @param event the instance that was passed to {@link BatchProcessingService#process}, not a copy, so callers can
 *              map it back to where it came from
 */
public record UnstoredEvent(IngestedEvent event, DeadLetterReason reason, String error) {
}
