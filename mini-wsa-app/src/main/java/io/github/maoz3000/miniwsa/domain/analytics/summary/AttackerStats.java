package io.github.maoz3000.miniwsa.domain.analytics.summary;

public record AttackerStats(String clientIp, long count, double avgThreatScore) {
}
