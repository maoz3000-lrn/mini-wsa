package io.github.maoz3000.miniwsa.domain.query;

import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;

import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for a filter. byCategory and byAction hold every category/action (zero when absent),
 * in declaration order, and always add up to totalEvents.
 */
public record StatsSummary(EventFilter filter, long totalEvents, Map<RuleCategory, CategoryStats> byCategory,
                           Map<Action, Long> byAction, List<AttackerStats> topAttackers,
                           List<PathStats> topTargetedPaths) {
}
