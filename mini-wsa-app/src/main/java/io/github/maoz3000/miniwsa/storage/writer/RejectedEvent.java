package io.github.maoz3000.miniwsa.storage.writer;

import io.github.maoz3000.miniwsa.domain.event.EnrichedEvent;

/**
 * An event the store refused individually; the rest of its batch was stored.
 */
public record RejectedEvent(EnrichedEvent event, String reason) {
}
