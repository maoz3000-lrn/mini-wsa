package io.github.maoz3000.miniwsa.storage.reader;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.event.Action;
import io.github.maoz3000.miniwsa.domain.event.EnrichedEvent;
import io.github.maoz3000.miniwsa.domain.event.RuleCategory;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.domain.analytics.summary.AttackerStats;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.BucketCount;
import io.github.maoz3000.miniwsa.domain.analytics.summary.CategoryStats;
import io.github.maoz3000.miniwsa.domain.analytics.EventFilter;
import io.github.maoz3000.miniwsa.domain.analytics.samples.PageRequest;
import io.github.maoz3000.miniwsa.domain.analytics.summary.PathStats;
import io.github.maoz3000.miniwsa.domain.analytics.summary.SummaryCounts;
import io.github.maoz3000.miniwsa.domain.analytics.timeseries.TimeInterval;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Repository
@ConditionalOnRole(Role.ANALYTICS)
public class ClickHouseEventAnalyticsReader implements EventAnalyticsReader {

    private final JdbcClient jdbc;
    private final AnalyticsQueryProperties properties;

    public ClickHouseEventAnalyticsReader(JdbcClient jdbc, AnalyticsQueryProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public SummaryCounts summaryCounts(EventFilter filter) {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, properties);
        // group_by_use_nulls: columns outside the current grouping set are NULL, which tells the sets apart
        List<GroupRow> rows = read(() -> jdbc.sql("""
                        SELECT toString(rule_category) AS category, toString(action) AS action,
                               count() AS cnt, avg(threat_score) AS avg_score
                        FROM %s%s
                        GROUP BY GROUPING SETS ((rule_category), (action), ())
                        %s, group_by_use_nulls = 1
                        """.formatted(sql.source(), sql.where(), sql.settings()))
                .paramSource(sql.parameters())
                .query((rs, n) -> new GroupRow(rs.getString("category"), rs.getString("action"),
                        rs.getLong("cnt"), rs.getDouble("avg_score")))
                .list());
        long total = rows.stream().filter(r -> r.category() == null && r.action() == null)
                .mapToLong(GroupRow::count).findFirst().orElse(0L);
        List<CategoryStats> byCategory = rows.stream().filter(r -> r.category() != null)
                .map(r -> new CategoryStats(RuleCategory.valueOf(r.category()), r.count(), r.avgScore()))
                .toList();
        Map<Action, Long> byAction = rows.stream().filter(r -> r.action() != null)
                .collect(Collectors.toMap(r -> Action.valueOf(r.action()), GroupRow::count,
                        Long::sum, () -> new EnumMap<>(Action.class)));
        return new SummaryCounts(total, byCategory, byAction);
    }

    @Override
    public List<AttackerStats> topAttackers(EventFilter filter, int limit) {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, properties);
        return read(() -> jdbc.sql("""
                        SELECT client_ip, count() AS cnt, avg(threat_score) AS avg_score
                        FROM %s%s
                        GROUP BY client_ip
                        ORDER BY cnt DESC, client_ip ASC
                        LIMIT :limit
                        %s
                        """.formatted(sql.source(), sql.where(), sql.settings()))
                .paramSource(withLimit(sql, limit))
                .query((rs, n) -> new AttackerStats(rs.getString("client_ip"), rs.getLong("cnt"), rs.getDouble("avg_score")))
                .list());
    }

    @Override
    public List<PathStats> topTargetedPaths(EventFilter filter, int limit) {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, properties);
        return read(() -> jdbc.sql("""
                        SELECT path, count() AS cnt
                        FROM %s%s
                        GROUP BY path
                        ORDER BY cnt DESC, path ASC
                        LIMIT :limit
                        %s
                        """.formatted(sql.source(), sql.where(), sql.settings()))
                .paramSource(withLimit(sql, limit))
                .query((rs, n) -> new PathStats(rs.getString("path"), rs.getLong("cnt")))
                .list());
    }

    @Override
    public List<EnrichedEvent> findSamples(EventFilter filter, PageRequest page) {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, properties);
        return read(() -> jdbc.sql("""
                        SELECT %s
                        FROM %s%s
                        ORDER BY timestamp DESC, event_id DESC
                        LIMIT :limit OFFSET :offset
                        %s
                        """.formatted(ClickHouseSampleRows.SELECT_LIST, sql.source(), sql.where(), sql.settings()))
                .paramSource(withLimit(sql, page.limit()).addValue("offset", page.offset()))
                .query((rs, n) -> ClickHouseSampleRows.toEnrichedEvent(rs))
                .list());
    }

    @Override
    public long countEvents(EventFilter filter) {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, properties);
        return read(() -> jdbc.sql("SELECT count() FROM %s%s%s".formatted(sql.source(), sql.where(), sql.settings()))
                .paramSource(sql.parameters())
                .query(Long.class)
                .single());
    }

    @Override
    public List<BucketCount> countsPerBucket(EventFilter filter, TimeInterval interval) {
        ClickHouseFilterSql sql = ClickHouseFilterSql.of(filter, properties);
        // Inserted fragments:
        //   source:   "dlr_events FINAL" when accuracy is EXACT (drops unmerged duplicates), else "dlr_events"
        //   where:    config_id = :configId (if given) AND :from <= timestamp <= :to (inclusive, UTC millis);
        //             filters on the raw timestamp column, so partition pruning and the primary key still apply
        //   settings: max_execution_time (query timeout -> 503) and, with FINAL,
        //             do_not_merge_across_partitions_select_final = 1 (deduplicate each day partition in parallel)
        return read(() -> jdbc.sql("""
                        -- Event counts per time bucket; only buckets that contain events are returned.
                        -- Bucket start = event time in epoch millis, rounded down to a multiple of the interval
                        -- (e.g. 5m: 10:07:42.500 -> 10:05:00.000). positiveModulo keeps the remainder >= 0, so
                        -- events before 1970 (negative millis) also round down, matching Java's Math.floorDiv.
                        SELECT toUnixTimestamp64Milli(timestamp)
                                 - positiveModulo(toUnixTimestamp64Milli(timestamp), :bucketMillis) AS bucket_ms,
                               count() AS cnt -- events per bucket
                        FROM %s%s
                        GROUP BY bucket_ms -- one row per bucket
                        ORDER BY bucket_ms -- oldest first, ready for a line chart
                        %s
                        """.formatted(sql.source(), sql.where(), sql.settings()))
                .paramSource(new MapSqlParameterSource(sql.parameters().getValues())
                        .addValue("bucketMillis", interval.duration().toMillis()))
                .query((rs, n) -> new BucketCount(Instant.ofEpochMilli(rs.getLong("bucket_ms")), rs.getLong("cnt")))
                .list());
    }

    private static MapSqlParameterSource withLimit(ClickHouseFilterSql sql, int limit) {
        return new MapSqlParameterSource(sql.parameters().getValues()).addValue("limit", limit);
    }

    private static <T> T read(Supplier<T> query) {
        try {
            return query.get();
        } catch (DataAccessException e) {
            throw new TransientStorageException("ClickHouse analytics query failed", e);
        }
    }

    private record GroupRow(String category, String action, long count, double avgScore) {
    }
}
