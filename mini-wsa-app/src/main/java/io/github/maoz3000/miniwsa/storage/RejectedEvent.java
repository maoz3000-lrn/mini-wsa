package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;

/**
 * An event the store refused individually; the rest of its batch was stored.
 */
public record RejectedEvent(EnrichedEvent event, String reason) {
}
