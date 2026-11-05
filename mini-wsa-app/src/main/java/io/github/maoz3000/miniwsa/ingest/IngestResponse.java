package io.github.maoz3000.miniwsa.ingest;

import io.github.maoz3000.miniwsa.domain.event.IngestedEvent;

import java.time.Instant;
import java.util.List;

/**
 * @param accepted   number of events durably accepted
 * @param receivedAt server-side arrival time stamped on every event of the batch
 */
public record IngestResponse(int accepted, Instant receivedAt) {

    /**
     * Summarizes a published batch. The batch is never empty (the parser rejects empty bodies) and every event in
     * it carries the same {@code receivedAt}.
     */
    public static IngestResponse from(List<IngestedEvent> ingested) {
        return new IngestResponse(ingested.size(), ingested.getFirst().receivedAt());
    }
}
