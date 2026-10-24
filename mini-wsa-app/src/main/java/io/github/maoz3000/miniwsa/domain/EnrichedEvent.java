package io.github.maoz3000.miniwsa.domain;

import java.time.Instant;

/**
 * An ingested event plus the enrichment computed by the processing pipeline.
 */
public record EnrichedEvent(IngestedEvent ingested, String attackType, int threatScore) {

    public DlrEvent event() {
        return ingested.event();
    }

    public Instant receivedAt() {
        return ingested.receivedAt();
    }
}
