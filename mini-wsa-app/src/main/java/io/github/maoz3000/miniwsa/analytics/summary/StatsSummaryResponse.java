package io.github.maoz3000.miniwsa.analytics.summary;

import io.github.maoz3000.miniwsa.analytics.TimeRangeResponse;
import io.github.maoz3000.miniwsa.domain.analytics.summary.CategoryStats;
import io.github.maoz3000.miniwsa.domain.analytics.summary.StatsSummary;
import io.github.maoz3000.miniwsa.domain.event.Action;
import io.github.maoz3000.miniwsa.domain.event.RuleCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregated statistics. byCategory and byAction list every category/action (zero when absent) and always add
 * up to totalEvents. Averages are rounded to one decimal.
 *
 * @param configId the requested configuration, or {@code null} when aggregated across all configurations
 */
public record StatsSummaryResponse(Long configId, TimeRangeResponse timeRange, long totalEvents,
                                   Map<RuleCategory, CategorySummary> byCategory, Map<Action, Long> byAction,
                                   List<TopAttacker> topAttackers, List<TopPath> topTargetedPaths) {

    public record CategorySummary(long count, double avgThreatScore) {
    }

    public record TopAttacker(String clientIp, long count, double avgThreatScore) {
    }

    public record TopPath(String path, long count) {
    }

    public static StatsSummaryResponse from(StatsSummary summary) {
        return new StatsSummaryResponse(
                summary.filter().configId(),
                TimeRangeResponse.from(summary.filter()),
                summary.totalEvents(),
                byCategory(summary.byCategory()),
                summary.byAction(),
                summary.topAttackers().stream()
                        .map(a -> new TopAttacker(a.clientIp(), a.count(), oneDecimal(a.avgThreatScore())))
                        .toList(),
                summary.topTargetedPaths().stream().map(p -> new TopPath(p.path(), p.count())).toList());
    }

    private static Map<RuleCategory, CategorySummary> byCategory(Map<RuleCategory, CategoryStats> stats) {
        return stats.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> new CategorySummary(e.getValue().count(), oneDecimal(e.getValue().avgThreatScore())),
                (a, b) -> a, () -> new EnumMap<>(RuleCategory.class)));
    }

    private static double oneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
