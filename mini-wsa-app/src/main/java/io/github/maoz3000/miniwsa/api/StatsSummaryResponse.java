package io.github.maoz3000.miniwsa.api;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics. byCategory and byAction list every category/action (zero when absent) and always add
 * up to totalEvents. Averages are rounded to one decimal.
 *
 * @param configId the requested configuration, or {@code null} when aggregated across all configurations
 */
public record StatsSummaryResponse(Long configId, TimeRange timeRange, long totalEvents,
                                   Map<RuleCategory, CategorySummary> byCategory, Map<Action, Long> byAction,
                                   List<TopAttacker> topAttackers, List<TopPath> topTargetedPaths) {

    public record TimeRange(Instant from, Instant to) {
    }

    public record CategorySummary(long count, double avgThreatScore) {
    }

    public record TopAttacker(String clientIp, long count, double avgThreatScore) {
    }

    public record TopPath(String path, long count) {
    }
}
