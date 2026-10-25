package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.CategorySummary;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TimeRange;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopAttacker;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopPath;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.AttackerStats;
import io.github.maoz3000.miniwsa.domain.query.CategoryStats;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PathStats;
import io.github.maoz3000.miniwsa.domain.query.StatsSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatsResponseMapperTest {

    private static final EventFilter FILTER = EventFilter.builder()
            .configId(14227L).from(Instant.parse("2026-05-20T00:00:00Z")).to(Instant.parse("2026-05-21T00:00:00Z")).build();

    @Test
    void roundsAveragesToOneDecimalAndKeepsCategoryOrder() {
        Map<RuleCategory, CategoryStats> byCategory = new EnumMap<>(Map.of(
                RuleCategory.INJECTION, new CategoryStats(RuleCategory.INJECTION, 2, 72.333333),
                RuleCategory.BOT, new CategoryStats(RuleCategory.BOT, 1, 45.05)));
        Map<Action, Long> byAction = new EnumMap<>(Map.of(Action.DENY, 2L, Action.ALERT, 0L, Action.MONITOR, 1L));
        StatsSummary summary = new StatsSummary(FILTER, 3, byCategory, byAction,
                List.of(new AttackerStats("203.0.113.42", 2, 81.25)), List.of(new PathStats("/api/v1/login", 2)));

        StatsSummaryResponse response = StatsResponseMapper.toResponse(summary);

        assertThat(response).isEqualTo(new StatsSummaryResponse(
                14227L,
                new TimeRange(FILTER.from(), FILTER.to()),
                3,
                Map.of(RuleCategory.INJECTION, new CategorySummary(2, 72.3),
                        RuleCategory.BOT, new CategorySummary(1, 45.1)),
                byAction,
                List.of(new TopAttacker("203.0.113.42", 2, 81.3)),
                List.of(new TopPath("/api/v1/login", 2))));
        assertThat(response.byCategory().keySet()).containsExactly(RuleCategory.INJECTION, RuleCategory.BOT);
    }
}
