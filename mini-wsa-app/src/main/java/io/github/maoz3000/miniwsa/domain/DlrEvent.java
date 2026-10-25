package io.github.maoz3000.miniwsa.domain;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;

/**
 * A security event (Data Log Record) exactly as sent by the edge. Only the fields processing depends on are
 * required here; the full input contract lives on the ingest request.
 */
@Builder(toBuilder = true)
public record DlrEvent(
        String eventId,
        Instant timestamp,
        Long configId,
        String policyId,
        String clientIp,
        String hostname,
        String path,
        String method,
        Integer statusCode,
        String userAgent,
        Rule rule,
        Action action,
        GeoLocation geoLocation,
        Long requestSize,
        Long responseSize) {

    public DlrEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(configId, "configId");
        Objects.requireNonNull(clientIp, "clientIp");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(action, "action");
    }
}
