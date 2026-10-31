package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.domain.DlrEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The JSON shape of a samples page, mirrored independently of {@link SamplesPage}.
 */
public record SamplesPageJson(long total, int limit, int offset, List<SampleEventJson> events) {

    /**
     * One sample as it appears on the wire: the DLR fields flattened to the top level, then the enrichment.
     * Enum values stay strings so their spelling on the wire is pinned.
     */
    public record SampleEventJson(String eventId, Instant timestamp, Long configId, String policyId, String clientIp,
                                  String hostname, String path, String method, Integer statusCode,
                                  String userAgent, RuleJson rule, String action, GeoLocationJson geoLocation,
                                  Long requestSize, Long responseSize, Instant receivedAt, String attackType,
                                  int threatScore) {

        public static SampleEventJson of(DlrEvent event, Instant receivedAt, String attackType, int threatScore) {
            return new SampleEventJson(event.eventId(), event.timestamp(), event.configId(), event.policyId(),
                    event.clientIp(), event.hostname(), event.path(), event.method(), event.statusCode(),
                    event.userAgent(), RuleJson.of(event), event.action().name(),
                    Optional.ofNullable(event.geoLocation())
                            .map(geo -> new GeoLocationJson(geo.country(), geo.city()))
                            .orElse(null),
                    event.requestSize(), event.responseSize(), receivedAt, attackType, threatScore);
        }
    }

    public record RuleJson(String id, String name, String message, String severity, String category) {

        static RuleJson of(DlrEvent event) {
            return new RuleJson(event.rule().id(), event.rule().name(), event.rule().message(),
                    event.rule().severity().name(), event.rule().category().name());
        }
    }

    public record GeoLocationJson(String country, String city) {
    }
}
