package io.github.maoz3000.miniwsa.api;

import java.time.Instant;

/**
 * The JSON shape of a successful ingest response, mirrored independently of {@link IngestResponse}.
 */
public record IngestResponseJson(int accepted, Instant receivedAt) {
}
