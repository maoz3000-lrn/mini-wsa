package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.GeoLocation;
import io.github.maoz3000.miniwsa.domain.IngestedEvent;
import io.github.maoz3000.miniwsa.domain.Rule;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.RuleSeverity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Reads {@code dlr_events} rows back into enriched events. Values are normalised in SQL (epoch millis, Int64,
 * enum names) so the mapping does not depend on driver-specific type handling.
 */
final class ClickHouseSampleRows {

    static final String SELECT_LIST = """
            event_id, toUnixTimestamp64Milli(timestamp) AS ts_ms, toUnixTimestamp64Milli(received_at) AS received_ms,
            toInt64(config_id) AS config_id, policy_id, client_ip, hostname, path, method,
            toInt32(status_code) AS status_code, user_agent, rule_id, rule_name, rule_message,
            toString(rule_severity) AS rule_severity, toString(rule_category) AS rule_category,
            toString(action) AS action, geo_country, geo_city,
            toInt64(request_size) AS request_size, toInt64(response_size) AS response_size,
            attack_type, toInt32(threat_score) AS threat_score""";

    private ClickHouseSampleRows() {
    }

    static EnrichedEvent toEnrichedEvent(ResultSet rs) throws SQLException {
        String country = rs.getString("geo_country");
        String city = rs.getString("geo_city");
        DlrEvent event = DlrEvent.builder()
                .eventId(rs.getString("event_id"))
                .timestamp(Instant.ofEpochMilli(rs.getLong("ts_ms")))
                .configId(rs.getLong("config_id"))
                .policyId(rs.getString("policy_id"))
                .clientIp(rs.getString("client_ip"))
                .hostname(rs.getString("hostname"))
                .path(rs.getString("path"))
                .method(rs.getString("method"))
                .statusCode(rs.getInt("status_code"))
                .userAgent(rs.getString("user_agent"))
                .rule(new Rule(rs.getString("rule_id"), rs.getString("rule_name"), rs.getString("rule_message"),
                        RuleSeverity.valueOf(rs.getString("rule_severity")),
                        RuleCategory.valueOf(rs.getString("rule_category"))))
                .action(Action.valueOf(rs.getString("action")))
                .geoLocation(country == null && city == null ? null : new GeoLocation(country, city))
                .requestSize(nullableLong(rs, "request_size"))
                .responseSize(nullableLong(rs, "response_size"))
                .build();
        return new EnrichedEvent(new IngestedEvent(event, Instant.ofEpochMilli(rs.getLong("received_ms"))),
                rs.getString("attack_type"), rs.getInt("threat_score"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
