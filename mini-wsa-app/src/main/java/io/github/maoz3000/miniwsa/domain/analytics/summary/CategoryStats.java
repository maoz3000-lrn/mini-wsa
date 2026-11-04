package io.github.maoz3000.miniwsa.domain.analytics.summary;

import io.github.maoz3000.miniwsa.domain.event.RuleCategory;

public record CategoryStats(RuleCategory category, long count, double avgThreatScore) {
}
