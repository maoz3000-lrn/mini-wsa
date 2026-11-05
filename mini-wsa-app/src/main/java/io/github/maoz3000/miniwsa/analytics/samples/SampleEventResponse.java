package io.github.maoz3000.miniwsa.analytics.samples;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.github.maoz3000.miniwsa.domain.event.EnrichedEvent;

import java.time.Instant;

/**
 * A stored event as returned to clients: the original DLR fields, flattened, plus the enrichment.
 */
public record SampleEventResponse(@JsonUnwrapped DlrEventResponse event, Instant receivedAt, String attackType,
                                  int threatScore) {

    public static SampleEventResponse from(EnrichedEvent e) {
        return new SampleEventResponse(DlrEventResponse.from(e.event()), e.receivedAt(), e.attackType(), e.threatScore());
    }
}
