package io.github.maoz3000.miniwsa.domain.query;

import io.github.maoz3000.miniwsa.domain.Action;

import java.util.List;
import java.util.Map;

/**
 * Totals computed from one consistent read: byCategory and byAction always add up to total.
 * Categories and actions without events are absent.
 */
public record SummaryCounts(long total, List<CategoryStats> byCategory, Map<Action, Long> byAction) {

    public SummaryCounts {
        byCategory = List.copyOf(byCategory);
        byAction = Map.copyOf(byAction);
    }
}
