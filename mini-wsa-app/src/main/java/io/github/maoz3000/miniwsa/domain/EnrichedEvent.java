package io.github.maoz3000.miniwsa.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * An ingested event plus the enrichment computed by the processing pipeline.
 */
public record EnrichedEvent(IngestedEvent ingested, String attackType, int threatScore) {

    public static final int MIN_THREAT_SCORE = 0;
    public static final int MAX_THREAT_SCORE = 100;

    public EnrichedEvent {
        Objects.requireNonNull(ingested, "ingested");
        Objects.requireNonNull(attackType, "attackType");
        if (threatScore < MIN_THREAT_SCORE || threatScore > MAX_THREAT_SCORE) {
            throw new IllegalArgumentException("threatScore must be within [" + MIN_THREAT_SCORE + ", "
                    + MAX_THREAT_SCORE + "]: " + threatScore);
        }
    }

    public DlrEvent event() {
        return ingested.event();
    }

    public Instant receivedAt() {
        return ingested.receivedAt();
    }
}
