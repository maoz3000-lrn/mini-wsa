package io.github.maoz3000.miniwsa.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The JSON shape of a stats summary, mirrored independently of {@link StatsSummaryResponse}. Map keys stay
 * strings so the category and action spellings on the wire are pinned.
 */
public record StatsSummaryJson(Long configId, TimeRange timeRange, long totalEvents,
                               Map<String, CategoryJson> byCategory, Map<String, Long> byAction,
                               List<TopAttackerJson> topAttackers, List<TopPathJson> topTargetedPaths) {

    public record TimeRange(Instant from, Instant to) {
    }

    public record CategoryJson(long count, double avgThreatScore) {
    }

    public record TopAttackerJson(String clientIp, long count, double avgThreatScore) {
    }

    public record TopPathJson(String path, long count) {
    }
}
