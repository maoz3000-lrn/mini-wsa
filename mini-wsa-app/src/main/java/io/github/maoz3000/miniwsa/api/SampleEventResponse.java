package io.github.maoz3000.miniwsa.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;

/**
 * A stored event as returned to clients: the original DLR fields, flattened, plus the enrichment.
 */
public record SampleEventResponse(@JsonUnwrapped DlrEventResponse event, Instant receivedAt, String attackType,
                                  int threatScore) {
}
