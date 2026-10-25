package io.github.maoz3000.miniwsa.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A validated event stamped with the server-side arrival time. This is the Kafka message payload.
 */
public record IngestedEvent(DlrEvent event, Instant receivedAt) {

    public IngestedEvent {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
