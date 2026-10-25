package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.domain.query.AttackerStats;
import io.github.maoz3000.miniwsa.domain.query.CategoryStats;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PathStats;
import io.github.maoz3000.miniwsa.domain.query.StatsSummary;
import io.github.maoz3000.miniwsa.domain.query.SummaryCounts;
import io.github.maoz3000.miniwsa.storage.EventAnalyticsReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private static final EventFilter FILTER = EventFilter.builder()
            .configId(14227L).from(Instant.parse("2026-05-20T00:00:00Z")).to(Instant.parse("2026-05-21T00:00:00Z")).build();

    private final EventAnalyticsReader reader = mock(EventAnalyticsReader.class);
    private final StatsService service = new StatsService(reader);

    @Test
    void buildsZeroFilledSummaryWithTopTen() {
        CategoryStats injection = new CategoryStats(RuleCategory.INJECTION, 2, 72.333333);
        CategoryStats bot = new CategoryStats(RuleCategory.BOT, 1, 45.05);
        AttackerStats attacker = new AttackerStats("203.0.113.42", 2, 81.25);
        PathStats path = new PathStats("/api/v1/login", 2);
        when(reader.summaryCounts(FILTER)).thenReturn(new SummaryCounts(3, List.of(injection, bot),
                Map.of(Action.DENY, 2L, Action.MONITOR, 1L)));
        when(reader.topAttackers(FILTER, StatsService.TOP_N)).thenReturn(List.of(attacker));
        when(reader.topTargetedPaths(FILTER, StatsService.TOP_N)).thenReturn(List.of(path));

        StatsSummary summary = service.summary(FILTER);

        assertThat(summary).isEqualTo(new StatsSummary(
                FILTER,
                3,
                Map.ofEntries(
                        entry(RuleCategory.INJECTION, injection),
                        entry(RuleCategory.XSS, new CategoryStats(RuleCategory.XSS, 0, 0.0)),
                        entry(RuleCategory.PROTOCOL_VIOLATION,
                                new CategoryStats(RuleCategory.PROTOCOL_VIOLATION, 0, 0.0)),
                        entry(RuleCategory.DATA_LEAKAGE, new CategoryStats(RuleCategory.DATA_LEAKAGE, 0, 0.0)),
                        entry(RuleCategory.BOT, bot),
                        entry(RuleCategory.DOS, new CategoryStats(RuleCategory.DOS, 0, 0.0)),
                        entry(RuleCategory.RATE_LIMIT, new CategoryStats(RuleCategory.RATE_LIMIT, 0, 0.0))),
                Map.of(Action.DENY, 2L, Action.ALERT, 0L, Action.MONITOR, 1L),
                List.of(attacker),
                List.of(path)));
        assertThat(summary.byCategory().keySet()).containsExactly(RuleCategory.values());
        assertThat(summary.byAction().keySet()).containsExactly(Action.values());
    }

    @Test
    void propagatesStorageFailureUnwrapped() {
        when(reader.summaryCounts(FILTER)).thenThrow(new TransientStorageException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.summary(FILTER)).isInstanceOf(TransientStorageException.class);
    }
}
