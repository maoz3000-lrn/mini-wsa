package io.github.maoz3000.miniwsa.domain.query;

public record AttackerStats(String clientIp, long count, double avgThreatScore) {
}
