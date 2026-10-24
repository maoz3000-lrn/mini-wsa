package io.github.maoz3000.miniwsa.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.github.maoz3000.miniwsa.domain.DlrEvent;

import java.time.Instant;

/**
 * A stored event as returned to clients: the original DLR fields, flattened, plus the enrichment.
 */
public record SampleEventResponse(@JsonUnwrapped DlrEvent event, Instant receivedAt, String attackType,
                                  int threatScore) {
}
