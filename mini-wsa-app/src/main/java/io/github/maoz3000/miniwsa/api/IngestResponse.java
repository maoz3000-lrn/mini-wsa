package io.github.maoz3000.miniwsa.api;

import java.time.Instant;

/**
 * @param accepted   number of events durably accepted
 * @param receivedAt server-side arrival time stamped on every event of the batch
 */
public record IngestResponse(int accepted, Instant receivedAt) {
}
