package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.CategorySummary;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TimeRange;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopAttacker;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopPath;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.CategoryStats;
import io.github.maoz3000.miniwsa.domain.query.StatsSummary;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a summary for clients; averages are rounded to one decimal.
 */
final class StatsResponseMapper {

    private StatsResponseMapper() {
    }

    static StatsSummaryResponse toResponse(StatsSummary summary) {
        return new StatsSummaryResponse(
                summary.filter().configId(),
                new TimeRange(summary.filter().from(), summary.filter().to()),
                summary.totalEvents(),
                byCategory(summary.byCategory()),
                summary.byAction(),
                summary.topAttackers().stream()
                        .map(a -> new TopAttacker(a.clientIp(), a.count(), Rounding.oneDecimal(a.avgThreatScore())))
                        .toList(),
                summary.topTargetedPaths().stream().map(p -> new TopPath(p.path(), p.count())).toList());
    }

    private static Map<RuleCategory, CategorySummary> byCategory(Map<RuleCategory, CategoryStats> stats) {
        return stats.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> new CategorySummary(e.getValue().count(), Rounding.oneDecimal(e.getValue().avgThreatScore())),
                (a, b) -> a, () -> new EnumMap<>(RuleCategory.class)));
    }
}
