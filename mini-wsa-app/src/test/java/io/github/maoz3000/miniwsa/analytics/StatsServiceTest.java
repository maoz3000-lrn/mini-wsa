package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.CategorySummary;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TimeRange;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopAttacker;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopPath;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import io.github.maoz3000.miniwsa.domain.query.AttackerStats;
import io.github.maoz3000.miniwsa.domain.query.CategoryStats;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PathStats;
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
    void buildsZeroFilledRoundedSummaryWithTopTen() {
        when(reader.summaryCounts(FILTER)).thenReturn(new SummaryCounts(3,
                List.of(new CategoryStats(RuleCategory.INJECTION, 2, 72.333333), new CategoryStats(RuleCategory.BOT, 1, 45.05)),
                Map.of(Action.DENY, 2L, Action.MONITOR, 1L)));
        when(reader.topAttackers(FILTER, StatsService.TOP_N)).thenReturn(List.of(new AttackerStats("203.0.113.42", 2, 81.25)));
        when(reader.topTargetedPaths(FILTER, StatsService.TOP_N)).thenReturn(List.of(new PathStats("/api/v1/login", 2)));

        assertThat(service.summary(FILTER)).isEqualTo(new StatsSummaryResponse(
                14227L,
                new TimeRange(FILTER.from(), FILTER.to()),
                3,
                Map.ofEntries(
                        entry(RuleCategory.INJECTION, new CategorySummary(2, 72.3)),
                        entry(RuleCategory.XSS, new CategorySummary(0, 0.0)),
                        entry(RuleCategory.PROTOCOL_VIOLATION, new CategorySummary(0, 0.0)),
                        entry(RuleCategory.DATA_LEAKAGE, new CategorySummary(0, 0.0)),
                        entry(RuleCategory.BOT, new CategorySummary(1, 45.1)),
                        entry(RuleCategory.DOS, new CategorySummary(0, 0.0)),
                        entry(RuleCategory.RATE_LIMIT, new CategorySummary(0, 0.0))),
                Map.of(Action.DENY, 2L, Action.ALERT, 0L, Action.MONITOR, 1L),
                List.of(new TopAttacker("203.0.113.42", 2, 81.3)),
                List.of(new TopPath("/api/v1/login", 2))));
    }

    @Test
    void propagatesStorageFailureUnwrapped() {
        when(reader.summaryCounts(FILTER)).thenThrow(new TransientStorageException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.summary(FILTER)).isInstanceOf(TransientStorageException.class);
    }
}
