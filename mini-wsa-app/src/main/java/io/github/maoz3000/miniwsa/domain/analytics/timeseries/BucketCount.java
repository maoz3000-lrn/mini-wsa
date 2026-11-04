package io.github.maoz3000.miniwsa.domain.analytics.timeseries;

import java.time.Instant;

/**
 * Number of events whose timestamp falls in the bucket starting at {@code start}.
 */
public record BucketCount(Instant start, long count) {
}
