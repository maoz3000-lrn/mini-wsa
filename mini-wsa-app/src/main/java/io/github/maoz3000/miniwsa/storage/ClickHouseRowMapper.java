package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.GeoLocation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts enriched events to {@code dlr_events} rows and checks every value fits its column, so the
 * database never rejects a batch because of one bad row. Problems are reported per event. Required fields and
 * the threat score range are guaranteed by the domain records, so only column limits are checked here.
 */
final class ClickHouseRowMapper {

    static final List<String> COLUMNS = List.of(
            "event_id", "timestamp", "received_at", "config_id", "policy_id", "client_ip", "hostname", "path",
            "method", "status_code", "user_agent", "rule_id", "rule_name", "rule_message", "rule_severity",
            "rule_category", "action", "geo_country", "geo_city", "request_size", "response_size",
            "attack_type", "threat_score");

    // DateTime64 supports [1900-01-01, 2299-12-31]
    private static final Instant MIN_TIME = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("2299-12-31T23:59:59.999Z");
    private static final int UINT16_MAX = 65_535;

    private ClickHouseRowMapper() {
    }

    record MappedBatch(List<Object[]> rows, List<RejectedEvent> rejected) {
    }

    static MappedBatch map(List<EnrichedEvent> events) {
        var partitioned = events.stream()
                .collect(Collectors.partitioningBy(e -> problem(e).isEmpty()));
        return new MappedBatch(
                partitioned.get(true).stream().map(ClickHouseRowMapper::toRow).toList(),
                partitioned.get(false).stream()
                        .map(e -> new RejectedEvent(e, problem(e).orElseThrow()))
                        .toList());
    }

    static Optional<String> problem(EnrichedEvent enriched) {
        DlrEvent e = enriched.event();
        return Stream.of(
                        outOfRange("timestamp", e.timestamp()), outOfRange("receivedAt", enriched.receivedAt()),
                        check(e.configId() <= 0, "configId must be positive"),
                        check(e.statusCode() != null && (e.statusCode() < 0 || e.statusCode() > UINT16_MAX),
                                "statusCode out of UInt16 range"),
                        check(e.requestSize() != null && e.requestSize() < 0, "requestSize must not be negative"),
                        check(e.responseSize() != null && e.responseSize() < 0, "responseSize must not be negative"),
                        check(textFields(enriched).anyMatch(ClickHouseRowMapper::hasLoneSurrogate),
                                "text contains invalid UTF-16 (unpaired surrogate)"))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Object[] toRow(EnrichedEvent enriched) {
        DlrEvent e = enriched.event();
        Optional<GeoLocation> geo = Optional.ofNullable(e.geoLocation());
        return new Object[]{
                e.eventId(), e.timestamp(), enriched.receivedAt(), e.configId(), e.policyId(), e.clientIp(),
                e.hostname(), e.path(), e.method(), e.statusCode(), e.userAgent(), e.rule().id(), e.rule().name(),
                e.rule().message(), e.rule().severity().name(), e.rule().category().name(), e.action().name(),
                geo.map(GeoLocation::country).orElse(null), geo.map(GeoLocation::city).orElse(null),
                e.requestSize(), e.responseSize(), enriched.attackType(), enriched.threatScore()};
    }

    private static Stream<String> textFields(EnrichedEvent enriched) {
        DlrEvent e = enriched.event();
        Stream<String> ruleTexts = Stream.of(e.rule().id(), e.rule().name(), e.rule().message());
        Stream<String> geoTexts = Optional.ofNullable(e.geoLocation())
                .map(g -> Stream.of(g.country(), g.city())).orElseGet(Stream::empty);
        return Stream.of(Stream.of(e.eventId(), e.policyId(), e.clientIp(), e.hostname(), e.path(), e.method(),
                        e.userAgent(), enriched.attackType()), ruleTexts, geoTexts)
                .flatMap(Function.identity());
    }

    private static boolean hasLoneSurrogate(String text) {
        // paired surrogates decode to supplementary code points; only unpaired ones remain in the surrogate range
        return text != null && text.codePoints().anyMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }

    private static Optional<String> outOfRange(String field, Instant value) {
        return check(value.isBefore(MIN_TIME) || value.isAfter(MAX_TIME),
                field + " outside DateTime64 range");
    }

    private static Optional<String> check(boolean failed, String message) {
        return failed ? Optional.of(message) : Optional.empty();
    }
}
