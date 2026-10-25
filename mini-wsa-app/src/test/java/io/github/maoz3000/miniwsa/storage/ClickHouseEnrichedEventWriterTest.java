package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.util.List;
import java.util.Map;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.EnrichedEventFixtures.enriched;
import static io.github.maoz3000.miniwsa.fixtures.EnrichedEventFixtures.enrichedExample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseEnrichedEventWriterTest {

    private JdbcTemplate jdbc;
    private ClickHouseEnrichedEventWriter writer;

    @BeforeEach
    void setUp() {
        jdbc = ClickHouseTestSupport.truncatedTable();
        writer = new ClickHouseEnrichedEventWriter(jdbc);
    }

    @Test
    void storesAllColumnsWithMillisecondPrecision() {
        EnrichedEvent event = enrichedExample();

        assertThat(writer.saveAll(List.of(event)).rejected()).isEmpty();

        assertThat(jdbc.queryForMap("""
                SELECT event_id, toString(timestamp) AS ts, toString(received_at) AS received, toInt64(config_id) AS config_id,
                       client_ip, path, toString(rule_severity) AS severity, toString(rule_category) AS category,
                       toString(action) AS action, geo_country, user_agent, toInt64(request_size) AS request_size, attack_type, toInt32(threat_score) AS threat_score
                FROM dlr_events
                """)).isEqualTo(Map.ofEntries(
                Map.entry("event_id", "evt-00132"),
                Map.entry("ts", "2026-05-20 14:32:10.000"),
                Map.entry("received", "2026-05-20 14:32:12.000"),
                Map.entry("config_id", 14227L),
                Map.entry("client_ip", "203.0.113.42"),
                Map.entry("path", "/api/v1/login"),
                Map.entry("severity", "CRITICAL"),
                Map.entry("category", "INJECTION"),
                Map.entry("action", "DENY"),
                Map.entry("geo_country", "CN"),
                Map.entry("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"),
                Map.entry("request_size", 1024L),
                Map.entry("attack_type", "SQL/Command Injection"),
                Map.entry("threat_score", 75)));
    }

    @Test
    void writesBatchAsSingleInsertAndReturnsRejectedRows() {
        List<EnrichedEvent> batch = List.of(
                enriched(validEvent().toBuilder().eventId("a").build(), 75),
                enriched(validEvent().toBuilder().eventId("bad").statusCode(70_000).build(), 75),
                enriched(validEvent().toBuilder().eventId("b").build(), 75));

        SaveResult result = writer.saveAll(batch);

        assertThat(result.rejected()).containsExactly(new RejectedEvent(batch.get(1), "statusCode out of UInt16 range"));
        assertThat(jdbc.queryForList("SELECT event_id FROM dlr_events ORDER BY event_id", String.class))
                .containsExactly("a", "b");
        assertThat(jdbc.queryForObject("SELECT count() FROM system.parts WHERE table = 'dlr_events' AND active",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void reSavingSameEventCollapsesToOneRow() {
        writer.saveAll(List.of(enrichedExample()));
        writer.saveAll(List.of(enrichedExample()));

        assertThat(jdbc.queryForObject("SELECT count() FROM dlr_events FINAL", Long.class)).isEqualTo(1L);
    }

    @Test
    void translatesUnreachableStoreToTransientFailure() {
        JdbcTemplate unreachable = new JdbcTemplate(new SimpleDriverDataSource(new com.clickhouse.jdbc.Driver(),
                "jdbc:clickhouse://localhost:1/miniwsa"));

        assertThatThrownBy(() -> new ClickHouseEnrichedEventWriter(unreachable).saveAll(List.of(enrichedExample())))
                .isInstanceOf(TransientStorageException.class);
    }
}
