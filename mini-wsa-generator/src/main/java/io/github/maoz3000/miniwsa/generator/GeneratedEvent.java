package io.github.maoz3000.miniwsa.generator;

import java.time.Instant;

/**
 * A DLR in the ingest API's JSON shape. The generator is an external client of the API, so it mirrors the wire
 * format instead of depending on the service's classes.
 */
public record GeneratedEvent(String eventId, Instant timestamp, long configId, String policyId, String clientIp,
                             String hostname, String path, String method, int statusCode, String userAgent,
                             Rule rule, String action, GeoLocation geoLocation, long requestSize,
                             long responseSize) {

    public record Rule(String id, String name, String message, String severity, String category) {
    }

    public record GeoLocation(String country, String city) {
    }
}
