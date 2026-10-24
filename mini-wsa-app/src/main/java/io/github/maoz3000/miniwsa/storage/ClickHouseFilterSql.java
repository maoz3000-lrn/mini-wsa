package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates an {@link EventFilter} and the read accuracy into ClickHouse SQL fragments with named parameters.
 */
final class ClickHouseFilterSql {

    // Instants are bound as explicit UTC text: binding java.sql.Timestamp would shift by the JVM's zone
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    private final String source;
    private final String where;
    private final String settings;
    private final MapSqlParameterSource parameters;

    private ClickHouseFilterSql(String source, String where, String settings, MapSqlParameterSource parameters) {
        this.source = source;
        this.where = where;
        this.settings = settings;
        this.parameters = parameters;
    }

    static ClickHouseFilterSql of(EventFilter filter, AnalyticsQueryProperties properties) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.configId() != null) {
            conditions.add("config_id = :configId");
            parameters.addValue("configId", filter.configId());
        }
        if (filter.from() != null) {
            conditions.add("timestamp >= toDateTime64(:from, 3, 'UTC')");
            parameters.addValue("from", utc(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add("timestamp <= toDateTime64(:to, 3, 'UTC')");
            parameters.addValue("to", utc(filter.to()));
        }
        if (filter.category() != null) {
            conditions.add("rule_category = :category");
            parameters.addValue("category", filter.category().name());
        }
        if (filter.action() != null) {
            conditions.add("action = :action");
            parameters.addValue("action", filter.action().name());
        }
        boolean exact = properties.accuracy() == QueryAccuracy.EXACT;
        List<String> settings = new ArrayList<>(List.of("max_execution_time = " + properties.queryTimeout().toSeconds()));
        if (exact) {
            // duplicates of an event share its partition (same timestamp), so FINAL can work partition by partition
            settings.add("do_not_merge_across_partitions_select_final = 1");
        }
        return new ClickHouseFilterSql(
                exact ? "dlr_events FINAL" : "dlr_events",
                conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions),
                settings.stream().collect(Collectors.joining(", ", " SETTINGS ", "")),
                parameters);
    }

    static String utc(Instant instant) {
        return UTC_MILLIS.format(instant);
    }

    /** Table expression, with {@code FINAL} when exact results are required. */
    String source() {
        return source;
    }

    /** {@code WHERE ...} clause (with leading space), or empty when unfiltered. */
    String where() {
        return where;
    }

    /** {@code SETTINGS ...} clause with leading space, to append at the very end of the query. */
    String settings() {
        return settings;
    }

    MapSqlParameterSource parameters() {
        return parameters;
    }
}
