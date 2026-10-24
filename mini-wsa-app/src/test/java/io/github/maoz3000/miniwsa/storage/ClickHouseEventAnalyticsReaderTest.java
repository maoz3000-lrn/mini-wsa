package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.AttackerStats;
import io.github.maoz3000.miniwsa.domain.query.CategoryStats;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PathStats;
import io.github.maoz3000.miniwsa.domain.query.SummaryCounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static io.github.maoz3000.miniwsa.fixtures.EnrichedEventFixtures.enriched;
import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseEventAnalyticsReaderTest {

    private static final Instant T0 = Instant.parse("2026-05-20T10:00:00Z");
    private static final AnalyticsQueryProperties EXACT =
            new AnalyticsQueryProperties(QueryAccuracy.EXACT, Duration.ofSeconds(30));

    private JdbcTemplate jdbc;
    private ClickHouseEnrichedEventWriter writer;
    private ClickHouseEventAnalyticsReader reader;

    @BeforeEach
    void setUp() {
        jdbc = ClickHouseTestSupport.truncatedTable();
        writer = new ClickHouseEnrichedEventWriter(jdbc);
        reader = new ClickHouseEventAnalyticsReader(JdbcClient.create(ClickHouseTestSupport.dataSource()), EXACT);
        writer.saveAll(List.of(
                event("e1", 1L, "10.0.0.1", "/login", RuleCategory.INJECTION, Action.DENY, T0, 90),
                event("e2", 1L, "10.0.0.1", "/login", RuleCategory.INJECTION, Action.DENY, T0.plusSeconds(1), 60),
                event("e3", 1L, "10.0.0.2", "/admin", RuleCategory.XSS, Action.ALERT, T0.plusSeconds(2), 40),
                event("e4", 1L, "10.0.0.3", "/home", RuleCategory.BOT, Action.MONITOR, T0.plusSeconds(3), 10),
                event("e5", 2L, "10.0.0.9", "/login", RuleCategory.INJECTION, Action.DENY, T0.plusSeconds(4), 75)));
    }

    @AfterEach
    void resumeMerges() {
        jdbc.execute("SYSTEM START MERGES dlr_events");
    }

    @Test
    void summarisesAllConfigurationsConsistently() {
        SummaryCounts summary = reader.summaryCounts(EventFilter.unfiltered());

        assertThat(summary).isEqualTo(new SummaryCounts(5,
                List.of(new CategoryStats(RuleCategory.INJECTION, 3, 75.0),
                        new CategoryStats(RuleCategory.XSS, 1, 40.0),
                        new CategoryStats(RuleCategory.BOT, 1, 10.0)),
                Map.of(Action.DENY, 3L, Action.ALERT, 1L, Action.MONITOR, 1L)));
    }

    @Test
    void appliesConfigurationAndInclusiveTimeBoundsToTheMillisecond() {
        EventFilter filter = EventFilter.builder().configId(1L).from(T0.plusSeconds(1)).to(T0.plusSeconds(2)).build();

        assertThat(reader.summaryCounts(filter).total()).isEqualTo(2);
        assertThat(reader.summaryCounts(filter.toBuilder().to(T0.plusSeconds(2).minusMillis(1)).build()).total())
                .isEqualTo(1);
    }

    @Test
    void summaryOfNoMatchingEventsIsEmpty() {
        assertThat(reader.summaryCounts(EventFilter.builder().configId(999L).build()))
                .isEqualTo(new SummaryCounts(0, List.of(), Map.of()));
    }

    @Test
    void ranksAttackersByCountThenIpWithLimit() {
        assertThat(reader.topAttackers(EventFilter.unfiltered(), 3)).containsExactly(
                new AttackerStats("10.0.0.1", 2, 75.0),
                new AttackerStats("10.0.0.2", 1, 40.0),
                new AttackerStats("10.0.0.3", 1, 10.0));
    }

    @Test
    void ranksPathsByCountThenPath() {
        assertThat(reader.topTargetedPaths(EventFilter.builder().configId(1L).build(), 10)).containsExactly(
                new PathStats("/login", 2), new PathStats("/admin", 1), new PathStats("/home", 1));
    }

    @Test
    void exactAccuracyIgnoresUnmergedDuplicatesWhileBestEffortCountsThem() {
        jdbc.execute("SYSTEM STOP MERGES dlr_events");
        writer.saveAll(List.of(event("e1", 1L, "10.0.0.1", "/login", RuleCategory.INJECTION, Action.DENY, T0, 90)));
        ClickHouseEventAnalyticsReader bestEffort = new ClickHouseEventAnalyticsReader(
                JdbcClient.create(ClickHouseTestSupport.dataSource()),
                new AnalyticsQueryProperties(QueryAccuracy.BEST_EFFORT, Duration.ofSeconds(30)));

        assertThat(reader.summaryCounts(EventFilter.unfiltered()).total()).isEqualTo(5);
        assertThat(bestEffort.summaryCounts(EventFilter.unfiltered()).total()).isEqualTo(6);
    }

    private static EnrichedEvent event(String id, long configId, String ip, String path, RuleCategory category,
                                       Action action, Instant timestamp, int score) {
        DlrEvent base = validEvent();
        return enriched(base.toBuilder()
                .eventId(id).configId(configId).clientIp(ip).path(path).action(action).timestamp(timestamp)
                .rule(base.rule().toBuilder().category(category).build())
                .build(), score);
    }
}
