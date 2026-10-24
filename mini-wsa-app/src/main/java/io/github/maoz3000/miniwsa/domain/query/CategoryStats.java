package io.github.maoz3000.miniwsa.domain.query;

import io.github.maoz3000.miniwsa.domain.RuleCategory;

public record CategoryStats(RuleCategory category, long count, double avgThreatScore) {
}
