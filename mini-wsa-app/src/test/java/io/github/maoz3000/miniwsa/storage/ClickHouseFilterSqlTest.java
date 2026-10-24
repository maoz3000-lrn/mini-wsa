package io.github.maoz3000.miniwsa.storage;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseFilterSqlTest {

    private static final AnalyticsQueryProperties EXACT = new AnalyticsQueryProperties(QueryAccuracy.EXACT, Duration.ofSeconds(30));
    private static final AnalyticsQueryProperties FAST = new AnalyticsQueryProperties(QueryAccuracy.BEST_EFFORT, Duration.ofSeconds(10));
    private static final Instant FROM = Instant.parse("2026-05-20T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-05-21T00:00:00Z");

    @Test
    void unfilteredQueryHasNoWhereClause() {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(EventFilter.unfiltered(), EXACT);

        assertThat(sql.where()).isEmpty();
        assertThat(sql.parameters().getValues()).isEmpty();
    }

    @Test
    void combinesEverySetConstraintWithInclusiveTimeBounds() {
        EventFilter filter = new EventFilter(14227L, FROM, TO, RuleCategory.XSS, Action.ALERT);

        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, EXACT);

        assertThat(sql.where()).isEqualTo(" WHERE config_id = :configId AND timestamp >= :from AND timestamp <= :to"
                + " AND rule_category = :category AND action = :action");
        assertThat(sql.parameters().getValues()).isEqualTo(Map.of(
                "configId", 14227L, "from", Timestamp.from(FROM), "to", Timestamp.from(TO),
                "category", "XSS", "action", "ALERT"));
    }

    @Test
    void exactAccuracyReadsWithFinalPerPartition() {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(EventFilter.unfiltered(), EXACT);

        assertThat(sql.source()).isEqualTo("dlr_events FINAL");
        assertThat(sql.settings())
                .isEqualTo(" SETTINGS max_execution_time = 30, do_not_merge_across_partitions_select_final = 1");
    }

    @Test
    void bestEffortAccuracyReadsWithoutFinal() {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(EventFilter.unfiltered(), FAST);

        assertThat(sql.source()).isEqualTo("dlr_events");
        assertThat(sql.settings()).isEqualTo(" SETTINGS max_execution_time = 10");
    }
}
